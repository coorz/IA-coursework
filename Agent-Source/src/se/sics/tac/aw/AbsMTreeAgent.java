/**
 * TAC AgentWare
 * http://www.sics.se/tac        tac-dev@sics.se
 *
 * Copyright (c) 2001-2005 SICS AB. All rights reserved.
 *
 * SICS grants you the right to use, modify, and redistribute this
 * software for noncommercial purposes, on the conditions that you:
 * (1) retain the original headers, including the copyright notice and
 * this text, (2) clearly document the difference between any derived
 * software and the original, and (3) acknowledge your use of this
 * software in pertaining publications and reports.  SICS provides
 * this software "as is", without any warranty of any kind.  IN NO
 * EVENT SHALL SICS BE LIABLE FOR ANY DIRECT, SPECIAL OR INDIRECT,
 * PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSSES OR DAMAGES ARISING OUT
 * OF THE USE OF THE SOFTWARE.
 *
 * -----------------------------------------------------------------
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 23 April, 2002
 * Updated : $Date: 2005/06/07 19:06:16 $
 *	     $Revision: 1.1 $
 * ---------------------------------------------------------
 * DummyAgent is a simplest possible agent for TAC. It uses
 * the TACAgent agent ware to interact with the TAC server.
 *
 * Important methods in TACAgent:
 *
 * Retrieving information about the current Game
 * ---------------------------------------------
 * int getGameID()
 *  - returns the id of current game or -1 if no game is currently plaing
 *
 * getServerTime()
 *  - returns the current server time in milliseconds
 *
 * getGameTime()
 *  - returns the time from start of game in milliseconds
 *
 * getGameTimeLeft()
 *  - returns the time left in the game in milliseconds
 *
 * getGameLength()
 *  - returns the game length in milliseconds
 *
 * int getAuctionNo()
 *  - returns the number of auctions in TAC
 *
 * int getClientPreference(int client, int type)
 *  - returns the clients preference for the specified type
 *   (types are TACAgent.{ARRIVAL, DEPARTURE, HOTEL_VALUE, E1, E2, E3}
 *
 * int getAuctionFor(int category, int type, int day)
 *  - returns the auction-id for the requested resource
 *   (categories are TACAgent.{CAT_FLIGHT, CAT_HOTEL, CAT_ENTERTAINMENT
 *    and types are TACAgent.TYPE_INFLIGHT, TACAgent.TYPE_OUTFLIGHT, etc)
 *
 * int getAuctionCategory(int auction)
 *  - returns the category for this auction (CAT_FLIGHT, CAT_HOTEL,
 *    CAT_ENTERTAINMENT)
 *
 * int getAuctionDay(int auction)
 *  - returns the day for this auction.
 *
 * int getAuctionType(int auction)
 *  - returns the type for this auction (TYPE_INFLIGHT, TYPE_OUTFLIGHT, etc).
 *
 * int getOwn(int auction)
 *  - returns the number of items that the agent own for this
 *    auction
 *
 * Submitting Bids
 * ---------------------------------------------
 * void submitBid(Bid)
 *  - submits a bid to the tac server
 *
 * void replaceBid(OldBid, Bid)
 *  - replaces the old bid (the current active bid) in the tac server
 *
 *   Bids have the following important methods:
 *    - create a bid with new Bid(AuctionID)
 *
 *   void addBidPoint(int quantity, float price)
 *    - adds a bid point in the bid
 *
 * Help methods for remembering what to buy for each auction:
 * ----------------------------------------------------------
 * int getAllocation(int auctionID)
 *   - returns the allocation set for this auction
 * void setAllocation(int auctionID, int quantity)
 *   - set the allocation for this auction
 *
 *
 * Callbacks from the TACAgent (caused via interaction with server)
 *
 * bidUpdated(Bid bid)
 *  - there are TACAgent have received an answer on a bid query/submission
 *   (new information about the bid is available)
 * bidRejected(Bid bid)
 *  - the bid has been rejected (reason is bid.getRejectReason())
 * bidError(Bid bid, int error)
 *  - the bid contained errors (error represent error status - commandStatus)
 *
 * quoteUpdated(Quote quote)
 *  - new information about the quotes on the auction (quote.getAuction())
 *    has arrived
 * quoteUpdated(int category)
 *  - new information about the quotes on all auctions for the auction
 *    category has arrived (quotes for a specific type of auctions are
 *    often requested at once).

 * auctionClosed(int auction)
 *  - the auction with id "auction" has closed
 *
 * transaction(Transaction transaction)
 *  - there has been a transaction
 *
 * gameStarted()
 *  - a TAC game has started, and all information about the
 *    game is available (preferences etc).
 *
 * gameStopped()
 *  - the current game has ended
 *
 */

package se.sics.tac.aw;
import se.sics.tac.util.ArgEnumerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.*;

public class AbsMTreeAgent extends AgentImpl {

  private static final Logger log =
    Logger.getLogger(AbsMTreeAgent.class.getName());

  private static final boolean DEBUG = false;

  private float[] prices;
  
  //Packages
  
  private static int[][] packages = new int[8][6];
  
  
  //Record all entertianmentNeeds.
  
  private static int[][] entertainmentNeedsMax = new int[3][1];
  private static float[] totalNeeds = new float[3];

  //Total times for this game.
  private static final long TOTAL_TIME = 9 * 60 * 1000;
  private long sellOrBuyTimes = 0;
 
  //Flight
  private float[][] ownFlight = new float[8][2];

  private float maxFlightPrice = Integer.MIN_VALUE;
  private float minFlightPrice = Integer.MAX_VALUE;

  private static final long Begin_Flight_Time = 2 * 60 * 1000;
  private static final long Second_Flight_Time = 6 * 60 * 1000;
  private static final long Last_Flight_Time = 8 * 60 * 1000;
  

  
  //Hotel
 
  private float[] hotelIncreaseRate = new float[8];
  private float[] lastHotelAskPrice = new float[8];
  private float[] lastHotelBitPrice = new float[8];
 
  private float[][] ownedHotel = new float[8][2];
  
  
  private class Price{
	  public ArrayList<PricesTime> prirces = new ArrayList<PricesTime>();
  }
  
  private class PricesTime{
	  public float price = 0;
	  public long time;
  }
  
  
  
  protected void init(ArgEnumerator args) {
    prices = new float[agent.getAuctionNo()];
  }
 

  public void quoteUpdated(Quote quote) {
    int auction = quote.getAuction();
    int auctionCategory = agent.getAuctionCategory(auction);
    
    if (auctionCategory == TACAgent.CAT_FLIGHT) {
    	ownFlight[auction][0] = agent.getOwn(auction);
    	ownFlight[auction][1] = quote.getBidPrice();
		
    	
    	
    	if ( maxFlightPrice < quote.getAskPrice()){
    		maxFlightPrice = quote.getAskPrice();
    	}
    	
    	if ( minFlightPrice > quote.getAskPrice()){
    		minFlightPrice = quote.getAskPrice();
    	}
    	
    	
    	int[] ownedHotelDay = new int[4];
    	
    	HashMap<Integer, Integer> canBuyInFlight = new HashMap<Integer, Integer>();
    	HashMap<Integer, Integer> canBuyOutFlight = new HashMap<Integer, Integer>();
    	for ( int i =0; i < 8; i++){
    		if ( i <= 3 ){
    			ownedHotelDay[i] += agent.getOwn(i + 8);
    			
    		}
    		else{
    			int x = i - 4;
    			ownedHotelDay[i - 4] += agent.getOwn(x + 12);
    		}
    	}
    	
    	boolean canBuy = true;
    	int maxFlightBuy = Integer.MAX_VALUE;
    	for ( int i = 0 ; i < 8 ; i ++){
    		maxFlightBuy = Integer.MAX_VALUE;
    		canBuy = true;
    		int in = packages[i][0] - 1 ;
    		int out = packages[i][1] - 1;
    		//int dayStay = in - out;
    		for ( int j = in; j < out ; j++){
    			if (ownedHotelDay[j] == 0){
    				canBuy = false;
    			}
    			if (maxFlightBuy > ownedHotelDay[j] && ownedHotelDay[j] != 0){
    				maxFlightBuy = ownedHotelDay[j];
    			}
    			
    		}
    		if ( canBuy){
    			if (canBuyInFlight.containsKey(in)){
    				canBuyInFlight.put(in, canBuyInFlight.get(in) > maxFlightBuy? maxFlightBuy: canBuyInFlight.get(in));
    			}else{
    				canBuyInFlight.put(in, maxFlightBuy);
    			}
    			if (canBuyOutFlight.containsKey(out)){
    				canBuyOutFlight.put(out, canBuyOutFlight.get(out) > maxFlightBuy? maxFlightBuy: canBuyOutFlight.get(out));
    			}else{
    				canBuyOutFlight.put(out, maxFlightBuy);
    			}
    			
    			
    		}
    	}
    
         
    	if (auction <=3 ){
    		maxFlightBuy = canBuyInFlight.get(auction);
    	}
    	else{
    		int x = auction -3;
    		maxFlightBuy = canBuyOutFlight.get(x);
    	}
    	
		 
		 int alloc = agent.getAllocation(auction) ;
 		if ( alloc > maxFlightBuy){
 			alloc = maxFlightBuy;
 		}
 		alloc = alloc - agent.getOwn(auction);
		
		 
		 
    	if ( alloc > 0){
    		
    		 if ( alloc > 0){
    			
    			 float averageLevel = (maxFlightPrice + minFlightPrice) / 2;
        		 float highLevel = (maxFlightPrice + minFlightPrice) * ( 2f / 3f) ; //Can change rate make it cheaper or not.
        		 float lowLevel = (maxFlightPrice + minFlightPrice ) * ( 1f / 3f) ; //Can chage rate make it cheaper or not.
        		 float askPrice = quote.getAskPrice();
    			 Bid bid = new Bid(auction);
        		 if (askPrice < minFlightPrice ){
        			 bid.addBidPoint(alloc, askPrice);
            		 
            		 agent.submitBid(bid);
        		 }
        		 else if ( askPrice <= averageLevel){                		 
            		 bid.addBidPoint(alloc, askPrice);
            		
            		 agent.submitBid(bid);
        		 }
        		 else if ( askPrice <= highLevel  && agent.getGameTime() >= Second_Flight_Time){                		 
            		 bid.addBidPoint(alloc, askPrice);
            		
            		 agent.submitBid(bid);
        		 }
        		 else if (agent.getGameTime() >= Last_Flight_Time ){    //If it is the last minutes.
        			 bid.addBidPoint(alloc, askPrice);
        			
        			 agent.submitBid(bid);
        		 }
    		
    		 }	 
    		 
    	
    	}
    }
    
    if (auctionCategory == TACAgent.CAT_HOTEL) {
    	ownedHotel[auction - 8][0] = agent.getOwn(auction);
    	ownedHotel[auction - 8][1] = quote.getBidPrice();
    	
    		
        if ( !(quote.isAuctionClosed() )){
        	
        	
        	
        	int alloc = agent.getAllocation(auction);
        	if (alloc > 0) {
        		
        		if ( lastHotelAskPrice[auction - 8] != 0 ){
            		hotelIncreaseRate[auction - 8] = quote.getAskPrice() / lastHotelAskPrice[auction - 8];
            	}
            	
            	lastHotelAskPrice[auction - 8] = quote.getAskPrice();
            	lastHotelBitPrice[auction - 8] = quote.getBidPrice();
            	Bid bid = new Bid(auction);
            	
            	prices[auction] = hotelIncreaseRate[auction - 8] * quote.getAskPrice() +  200; // Plus a number can be changed for successfully bid.
            	
        		
        		
     
        		float maxBenefit = Float.MIN_VALUE;
            	for (int i = 0 ; i < 8; i++){
            		if (maxBenefit < packages[i][2]){
            			maxBenefit = packages[i][2];
            		}
            		
           		 }
           		 if ( prices[auction]  >= maxBenefit + 700   ){
           			prices[auction]  = maxBenefit + 700 ;
           			bid.addBidPoint(alloc, prices[auction]);
           			 agent.submitBid(bid);
           		 }
           		 else{
           			 bid.addBidPoint(alloc, prices[auction]);
           			agent.submitBid(bid);
           		 }
          			 
           			
        		
    			
        	}
        }
    	

    } else if (auctionCategory == TACAgent.CAT_ENTERTAINMENT) { //Only sell.
    	int own = agent.getOwn(auction);
    	int alloc = agent.getAllocation(auction);
        Bid bid = new Bid(auction);
        if (own > 0 && own > alloc) {
	      	int type = calculateEType(auction);
	      	
	  	    int maxE = entertainmentNeedsMax[type][0];
	  	  
	  	    
	  	
            if ( agent.getGameTime() >= Last_Flight_Time)	{
            	prices[auction] = 20;
            	if ( prices[auction] < 0 )
            		prices[auction] = 20;
            }
	  		
            else {
            	prices[auction] = maxE - (((float)agent.getGameTime() / (TOTAL_TIME - Last_Flight_Time))) * ( maxE ) ; //Rate can be changed.
    			if ( prices[auction] < maxE * (1f/2f) ){
    				prices[auction] = maxE * (1f/2f);
    			}
            }
			
			// prices[auction] = prices[auction] * Math.abs(alloc);
	  		bid.addBidPoint( alloc - own, prices[auction]);
	  		if (DEBUG) {
	  		  log.finest("submitting bid with alloc="
	  			     + agent.getAllocation(auction)
	  			     + " own=" + agent.getOwn(auction));
	  		}
	  		sellOrBuyTimes++;
	  		agent.submitBid(bid);
   
     
        }
    }
  }
 
  
//  public boolean isWorthBuying(int auction, int auctionCat, float price){
//	 
//	  if (auctionCat == TACAgent.CAT_FLIGHT) {
//		  float flightPrice = price;
//		  
//		  float[] flightBenefits = new float[8];
//		  float u = 1000 - price;
//		  
//		  int max = Integer.MIN_VALUE;
//		  int min = Integer.MAX_VALUE;
//		  
//		  for (int i = 0 ; i < packages.length; i++){
//			  int in = packages[i][0];
//			  int out = packages[i][1];
//			  if ( auction <= 3 && in == auction ){
//				  int bought  
//			  }
//			  if ( auction > 3 && out == auction - 3){
//				  
//			  }
//		  }
//		  
////		  for (int i = 0 ; i < packages.length; i++){
////			  if (auction <= 3){
////				  if (packages[i][0] >= price){
////					  return true;
////				  }  
////			  }
////			  else {
////				  if (packages[i][1] >= price){
////					  return true;
////				  }  
////			  }
////			  
////		  }
//		  
//	  }
//	  else if (auctionCat == TACAgent.CAT_HOTEL) {
//		  
//		  
//	  }
//	  
//	  return false;
//	  
//  }
  


  public void quoteUpdated(int auctionCategory) {
    log.fine("All quotes for "
	     + agent.auctionCategoryToString(auctionCategory)
	     + " has been updated");
  }

  public void bidUpdated(Bid bid) {
    log.fine("Bid Updated: id=" + bid.getID() + " auction="
	     + bid.getAuction() + " state="
	     + bid.getProcessingStateAsString());
    log.fine("       Hash: " + bid.getBidHash());
  }

  public void bidRejected(Bid bid) {
    log.warning("Bid Rejected: " + bid.getID());
    log.warning("      Reason: " + bid.getRejectReason()
		+ " (" + bid.getRejectReasonAsString() + ')');
  }

  public void bidError(Bid bid, int status) {
    log.warning("Bid Error in auction " + bid.getAuction() + ": " + status
		+ " (" + agent.commandStatusToString(status) + ')');
  }

  public void gameStarted() {
    log.fine("Game " + agent.getGameID() + " started!");

    calculateAllocation();
    sendBids();
  }

  public void gameStopped() {
    log.fine("Game Stopped!");
  }

  public void auctionClosed(int auction) {
    log.fine("*** Auction " + auction + " closed!");
  }

  private void sendBids() {
   
	  
	//Calculate all averages and maxE/minE here.  
	  
	  
	  int sumE1 = 0;
	  int sumE2 = 0;
	  int sumE3 = 0;
	  for (int i = 0; i < 3; i++){
		 
		  int maxE = Integer.MIN_VALUE;
		  
		  
		  for ( int j = 0; j < packages.length ; j++){
			  if ( maxE < packages[j][i + 3]){
				  maxE = packages[j][i + 3];
			  }
			  
			  if ( i == 0){
				  sumE1 += packages[j][i + 3];
				  
			  }
			  if ( i == 1){
				  sumE2 += packages[j][i + 3];
				  
			  }
			  if ( i == 2){
				  sumE3 += packages[j][i + 3];
				  
			  }
		  }
		  
		  entertainmentNeedsMax[i][0] = maxE;
		 
	  }
	 
	  
	  totalNeeds[0] = sumE1;
	  totalNeeds[1] = sumE2;
	  totalNeeds[2] = sumE3;
	  
	    
	
    for (int i = 0, n = agent.getAuctionNo(); i < n; i++) {
      int alloc = agent.getAllocation(i) - agent.getOwn(i);
      float price = -1f;
      switch (agent.getAuctionCategory(i)) {
//      case TACAgent.CAT_FLIGHT:
//		if (alloc > 0) {
//		  price = 1000;
//		}
//		break;
      case TACAgent.CAT_HOTEL:
			if (alloc > 0) {
			  price = 200;
			  prices[i] = 200;
			}
			break;
//      case TACAgent.CAT_ENTERTAINMENT:
//    	  //If we need to sell, we set a higher price which is the average of the all client's prices.
//    	  if ( agent.getOwn(i) > 0){
//    		  Bid bid = new Bid(i);
//    		  
//    		  price = entertainmentNeedsMax[calculateEType(i)][0];
//    		  prices[i] = price;
//    		  bid.addBidPoint(-agent.getOwn(i), price);
//    		  agent.submitBid(bid);
//          }
//    	  
//	break;
      default:
	break;
      }
      if (price > 0) {
	//Bid bid = new Bid(i);
	//bid.addBidPoint(alloc, price);
	if (DEBUG) {
	  log.finest("submitting bid with alloc=" + agent.getAllocation(i)
		     + " own=" + agent.getOwn(i));
	}
	//agent.submitBid(bid);
      }
    }
  }
  
  private int calculateEType(int i){
	  if ( i >= 16 && i <= 19){
		 return 0;
	  }
	  else if ( i >= 20 && i <= 23){
		 return 1;
	  }
	  else if ( i >= 24 && i <= 27){
		  return 2;
	  }
	  return 0;
  }

  private void calculateAllocation() {
	  int totalHotelNeeds = 0;
	  for (int i = 0; i < 8; i++) {
	      int hotel = agent.getClientPreference(i, TACAgent.HOTEL_VALUE);
	      totalHotelNeeds += hotel;
	  }
	  float goodHotelDoor = (totalHotelNeeds / 8 );
	  
	  
    for (int i = 0; i < 8; i++) {
    
      int inFlight = agent.getClientPreference(i, TACAgent.ARRIVAL);
      int outFlight = agent.getClientPreference(i, TACAgent.DEPARTURE);
      int hotel = agent.getClientPreference(i, TACAgent.HOTEL_VALUE);
      packages[i][0] = inFlight;
      packages[i][1] = outFlight;
      packages[i][2] = hotel;
      
      
      int type;

      // Get the flight preferences auction and remember that we are
      // going to buy tickets for these days. (inflight=1, outflight=0)
      int auction = agent.getAuctionFor(TACAgent.CAT_FLIGHT,
					TACAgent.TYPE_INFLIGHT, inFlight);
      agent.setAllocation(auction, agent.getAllocation(auction) + 1);
      auction = agent.getAuctionFor(TACAgent.CAT_FLIGHT,
				    TACAgent.TYPE_OUTFLIGHT, outFlight);
      agent.setAllocation(auction, agent.getAllocation(auction) + 1);

      // if the hotel value is greater than 70 we will select the
      // expensive hotel (type = 1)
      if (hotel >= goodHotelDoor) {
	type = TACAgent.TYPE_GOOD_HOTEL;
      } else {
	type = TACAgent.TYPE_CHEAP_HOTEL;
      }
      //type = TACAgent.TYPE_GOOD_HOTEL;
      // allocate a hotel night for each day that the agent stays
      for (int d = inFlight; d < outFlight; d++) {
	auction = agent.getAuctionFor(TACAgent.CAT_HOTEL, type, d);
	log.finer("Adding hotel for day: " + d + " on " + auction);
	agent.setAllocation(auction, agent.getAllocation(auction) + 1);
      }

      int eType = -1;
      while((eType = nextEntType(i, eType)) > 0) {
	auction = bestEntDay(inFlight, outFlight, eType);
	log.finer("Adding entertainment " + eType + " on " + auction);
	agent.setAllocation(auction, agent.getAllocation(auction) + 1);
      }
    }
  }

  private int bestEntDay(int inFlight, int outFlight, int type) {
    for (int i = inFlight; i < outFlight; i++) {
      int auction = agent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT,
					type, i);
      if (agent.getAllocation(auction) < agent.getOwn(auction)) {
	return auction;
      }
    }
    // If no left, just take the first...
    return agent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT,
			       type, inFlight);
  }

  private int nextEntType(int client, int lastType) {
    int e1 = agent.getClientPreference(client, TACAgent.E1);
    int e2 = agent.getClientPreference(client, TACAgent.E2);
    int e3 = agent.getClientPreference(client, TACAgent.E3);
    packages[client][3] = e1;
    packages[client][4] = e2;
    packages[client][5] = e3;
    
    // At least buy what each agent wants the most!!!
    //Make client's needs in order. Descent.
    if ((e1 > e2) && (e1 > e3) && lastType == -1){
    	
    	return TACAgent.TYPE_ALLIGATOR_WRESTLING;
    }
      
    if ((e2 > e1) && (e2 > e3) && lastType == -1){
    	
    	return TACAgent.TYPE_AMUSEMENT;
    }
      
    if ((e3 > e1) && (e3 > e2) && lastType == -1){
    	return TACAgent.TYPE_MUSEUM;
    }
      
    return -1;
  }



  // -------------------------------------------------------------------
  // Only for backward compability
  // -------------------------------------------------------------------

  public static void main (String[] args) {
    TACAgent.main(args);
  }

} // DummyAgent
