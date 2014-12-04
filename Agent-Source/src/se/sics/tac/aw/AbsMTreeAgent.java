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
import java.util.logging.*;

public class AbsMTreeAgent extends AgentImpl {

  private static final Logger log =
    Logger.getLogger(AbsMTreeAgent.class.getName());

  private static final boolean DEBUG = false;

  private float[] prices;
  
  //Record all entertianmentNeeds.
  private static int[][] entertainmentNeeds = new int[8][3];
  private static int[][] entertainmentNeedsMaxMin = new int[3][2];
  private static float[] totalNeeds = new float[3];
  private static float[] totalNeedsAverage = new float[3];
  //Total times for this game.
  private static final long TOTAL_TIME = 9 * 60 * 1000;
 
  //Flight
  private int[] ownFlight = new int[8];
  Price[] flightPrice = new Price[8];
  private float[] maxFlightPrice = new float[8];
  private float[] minFlightPrice = new float[8];
  private boolean[] bided = new boolean[8];
  private static final long Begin_Flight_Time = 4 * 60 * 1000;
  private static final long Second_Flight_Time = 6 * 60 * 1000;
  private static final long Last_Flight_Time = 8 * 60 * 1000;
  
  private float maxFlightPriceInALl = Integer.MIN_VALUE;
  private float minFlightPriceInALl = Integer.MAX_VALUE;
  
  
  //Hotel
 
  private float[] hotelIncreaseRate = new float[8];
  private float[] lastHotelAskPrice = new float[8];
  private long lastSecondsOfEachMinute = 20 * 1000;
  
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
    	int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
    	if (alloc > 0 ){
    		PricesTime p = new PricesTime();
        	p.price = quote.getAskPrice();
        	p.time = agent.getGameTime();
        	flightPrice[auction].prirces.add(p);
        	if (maxFlightPriceInALl < quote.getAskPrice()){
        		maxFlightPriceInALl = quote.getAskPrice();
        	}
        	
        	if (minFlightPriceInALl > quote.getAskPrice()){
        		minFlightPriceInALl = quote.getAskPrice();
        	}
        	
        	if ( maxFlightPrice[auction] < quote.getAskPrice()){
        		maxFlightPrice[auction] = quote.getAskPrice();
        	}
        	
        	if ( minFlightPrice[auction] > quote.getAskPrice()){
        		minFlightPrice[auction] = quote.getAskPrice();
        	}
        	
        	
        	//If after Begin_Flight_Time.
        	if ( agent.getGameTime() >= Begin_Flight_Time ){
        		 
        		 float highLevel = (maxFlightPrice[auction] + minFlightPrice[auction]) * ( 2f / 3f) ; //Can change rate make it cheaper or not.
        		 float lowLevel = (maxFlightPrice[auction] + minFlightPrice[auction]) * ( 1f / 3f) ; //Can chage rate make it cheaper or not.
        		 float askPrice = quote.getAskPrice();
        		 
        		 Bid bid = new Bid(auction);
        		 if (askPrice < minFlightPrice[auction]){
        			 bid.addBidPoint(alloc, askPrice);
            		 bided[auction] = true;
            		 agent.submitBid(bid);
        		 }
        		 
        		 else if ( askPrice <= lowLevel && !(bided[auction])){
        			 
            		 
            		 bid.addBidPoint(alloc, askPrice);
            		 bided[auction] = true;
            		 agent.submitBid(bid);
        		 }
        		 else if (askPrice <= (maxFlightPrice[auction] + minFlightPrice[auction]) / 2 && !(bided[auction]) ){ // If it is after Second_Flight_Time.
        			 bid.addBidPoint(alloc, askPrice);
        			 bided[auction] = true;
        			 agent.submitBid(bid);
        		 }
        		 else if ( agent.getGameTime() >= Last_Flight_Time && !(bided[auction])){    //If it is the last minutes.
        			 //bid.addBidPoint(alloc, askPrice);
        			 //bided[auction] = true;
        			 //agent.submitBid(bid);
        		 }
        		 
        		 
        	}
    	}
    	
    	
    	
//    	ownFlight[auctionCategory] = agent.getOwn(auction);
//    	PricesTime fpt = new PricesTime();
//    	fpt.time = agent.getGameTime();
//    	fpt.price = quote.getBidPrice();
//    	flightPrice[auction].prirces.add(fpt);
    }
    
    if (auctionCategory == TACAgent.CAT_HOTEL) {

        if ( !(quote.isAuctionClosed() )){
        	
        	
        	int alloc = agent.getAllocation(auction);
        	if (alloc > 0 && quote.hasHQW(agent.getBid(auction)) && quote.getHQW() < alloc) {
        		
        		if ( lastHotelAskPrice[auction - 8] != 0 ){
            		hotelIncreaseRate[auction - 8] = quote.getAskPrice() / lastHotelAskPrice[auction - 8];
            	}
            	
            	lastHotelAskPrice[auction - 8] = quote.getAskPrice();
            	Bid bid = new Bid(auction);
            	
            		
            		prices[auction] = hotelIncreaseRate[auction - 8] * quote.getAskPrice() + 30; // Plus a number can be changed for successfully bid.
            		bid.addBidPoint(alloc, prices[auction]);
            		agent.submitBid(bid);

    			
        	}
        }
    	
//	      int alloc = agent.getAllocation(auction);
//	      if (alloc > 0 && quote.hasHQW(agent.getBid(auction)) &&
//		  quote.getHQW() < alloc) {
//			Bid bid = new Bid(auction);
//			// Can not own anything in hotel auctions...
//			prices[auction] = quote.getAskPrice() + 50;
//			bid.addBidPoint(alloc, prices[auction]);
//			if (DEBUG) {
//			  log.finest("submitting bid with alloc="
//				     + agent.getAllocation(auction)
//				     + " own=" + agent.getOwn(auction));
//			}
//			agent.submitBid(bid);
//      }
    } else if (auctionCategory == TACAgent.CAT_ENTERTAINMENT) {
    	
      int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
      Bid bid = new Bid(auction);
      if (alloc != 0) {
    	int type = calculateEType(auction);
    	int minE = entertainmentNeedsMaxMin[type][1];
	    //int maxE = entertainmentNeedsMaxMin[type][0];
	    float averageNeed = totalNeedsAverage[type];
	    
		if (alloc < 0){
			//Sell price. 
			//Single price;
			float bidPrice = quote.getBidPrice();
			prices[auction] = averageNeed - (((float)agent.getGameTime() / (TOTAL_TIME))) * ( averageNeed ); //Rate can be changed.
			
			if ( prices[auction] >= bidPrice && bidPrice >= averageNeed){
				prices[auction] = bidPrice;
			}
		    	
		}
			
		else{
			//Buy price.
			//Single price.
			
			prices[auction] = minE + (((float)agent.getGameTime() / TOTAL_TIME)) * ( averageNeed) * 1.5f; //Rate can be changed.
			float totalBenifit = 0;
			for (int i = 0 ; i < 8; i++){
				totalBenifit += entertainmentNeeds[i][type] - prices[auction];
			}
			if ( totalBenifit < 0 ){
				prices[auction] = 0;
			}
      	  }
	
		}
      
     // prices[auction] = prices[auction] * Math.abs(alloc);
		bid.addBidPoint(alloc, prices[auction]);
		if (DEBUG) {
		  log.finest("submitting bid with alloc="
			     + agent.getAllocation(auction)
			     + " own=" + agent.getOwn(auction));
		}
		agent.submitBid(bid);
      }
  }
 
  


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
    for (int i = 0 ; i < 8; i++){
    	Price p = new Price();
    	flightPrice[i] = p;
    	maxFlightPrice[i] = Integer.MIN_VALUE;
    	minFlightPrice[i] = Integer.MAX_VALUE;
    	
    }
	  
	  
	  
	  
	//Calculate all averages and maxE/minE here.  
	  
	  
	  int sumE1 = 0;
	  int sumE2 = 0;
	  int sumE3 = 0;
	  for (int i = 0; i < 3; i++){
		 
		  int maxE = Integer.MIN_VALUE;
		  int minE = Integer.MAX_VALUE;
		  
		  for ( int j = 0; j < entertainmentNeeds.length ; j++){
			  if ( maxE < entertainmentNeeds[j][i]){
				  maxE = entertainmentNeeds[j][i];
			  }
			  if ( minE > entertainmentNeeds[j][i]){
				  minE = entertainmentNeeds[j][i];
			  }
			  if ( i == 0){
				  sumE1 += entertainmentNeeds[j][i];
				  
			  }
			  if ( i == 1){
				  sumE2 += entertainmentNeeds[j][i];
				  
			  }
			  if ( i == 2){
				  sumE3 += entertainmentNeeds[j][i];
				  
			  }
		  }
		  
		  entertainmentNeedsMaxMin[i][0] = maxE;
		  entertainmentNeedsMaxMin[i][1] = minE;
	  }
	  totalNeedsAverage[0] = sumE1 / 8f;
	  totalNeedsAverage[1] = sumE2 / 8f;
	  totalNeedsAverage[2] = sumE3 / 8f;
	  
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
			  price = 50;
			  prices[i] = 50f;
			}
			break;
      case TACAgent.CAT_ENTERTAINMENT:
    	  //If we need to sell, we set a higher price which is the average of the all client's prices.
    	  if (alloc < 0){
        	  
    		  //prices[i] = totalNeedsAverage[calculateEType(i)] * Math.abs(alloc);
    		  price = totalNeedsAverage[calculateEType(i)] * Math.abs(alloc);
          }
    	 //Buy the entertainment. We buy the ticket with the lowes's price among the 8 clients.
          if (alloc > 0){
        	        	 
        	  price = entertainmentNeedsMaxMin[calculateEType(i)][1] * alloc;
          }
    	  
	break;
      default:
	break;
      }
      if (price > 0) {
	Bid bid = new Bid(i);
	bid.addBidPoint(alloc, price);
	if (DEBUG) {
	  log.finest("submitting bid with alloc=" + agent.getAllocation(i)
		     + " own=" + agent.getOwn(i));
	}
	agent.submitBid(bid);
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
    for (int i = 0; i < 8; i++) {
      int inFlight = agent.getClientPreference(i, TACAgent.ARRIVAL);
      int outFlight = agent.getClientPreference(i, TACAgent.DEPARTURE);
      int hotel = agent.getClientPreference(i, TACAgent.HOTEL_VALUE);
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
      if (hotel > 70) {
	type = TACAgent.TYPE_GOOD_HOTEL;
      } else {
	type = TACAgent.TYPE_CHEAP_HOTEL;
      }
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
    
    //Record every client's entertainment needs.
    entertainmentNeeds[client][0] = e1;
    entertainmentNeeds[client][1] = e2;
    entertainmentNeeds[client][2] = e3;

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
