package Logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import NetBase.ManagedConnection;
import NetBase.ManagedConnection.ConnectionState;
import NetBase.MessageParser.Request;
import NetBase.MessageParser.Response;
import Primitives.BitMap;
import Primitives.Piece;

/**
 * This class was constructed to make peer logic 
 * and management easier.
 * 
 * This class manages dissemination of pieces across
 * managed connections.
 * 
 * This class also maintains actively worked on pieces.
 * 
 * -Tracks which pieces are being worked on 
 * -Tracks which connections are working on which pieces
 * -Provides an interface for incoming blocks to be completed.
 * -
 * TODO: edge 1, client leaves block requeue'd but no one able to complete
 * @author wiselion
 */
public class Disseminator {
	private class ConnectionWork{
		Set<Piece> queued = new TreeSet<Piece>();
		List<Request> blockLeft = new LinkedList<Request>();//blocks queued from pieces but not yet sent.
	}
	
	//List of things disseminated
	//Managed connection.
	private Map<ManagedConnection, ConnectionWork> clientToPieceSet = new HashMap<ManagedConnection, ConnectionWork>();
	//for sake of completeness. Build it correct now so i don't have to come back
	private Map<Piece,List<ManagedConnection>> pieceToClients = new HashMap<Piece,List<ManagedConnection>>();
	private Set<Piece> currentQueue = new TreeSet<Piece>();//Queued up pieces. But none given out.
	private Map<Integer,Piece> disseminatedPiecesToCompete = new HashMap<Integer,Piece>();//pieces that are given to at least 1 connection
	private Map<Integer,Piece> otherPiecesGettingComplete = new HashMap<Integer,Piece>();//(why? well we don't exactly know.)
	private List<Piece> recentlyCompleted = new ArrayList<Piece>();
	
	public void connectionCleanUp(ManagedConnection mc){
		Piece [] ps = clientToPieceSet.get(mc).queued.toArray(new Piece[0]);
		if(ps!=null){
			for(Piece p  : ps){
				cancelPieceForConnection(mc,(int)p.pieceIndex);
			}
		}
		clientToPieceSet.remove(mc);
	}
	
	/**
	 * This function enqueues at max "maxPieces" pieces to
	 * this connection.
	 * 
	 * Pieces are removed from the current Queue.
	 * 
	 * Note that connection will not add more
	 * then maxPieces to the connection.
	 * 
	 * @param maxPieces
	 * @param mc
	 */
	public void enqueuePieces(int maxPieces, ManagedConnection mc){
		Iterator<Piece> itor = currentQueue.iterator();
		ConnectionWork cw = clientToPieceSet.get(mc);
		while(itor.hasNext()){
			Piece p = itor.next();
			if(cw.queued.size() +1 >maxPieces){
				break;
			}
			
			if(mc.getPeerBitmap().hasPiece((int)p.pieceIndex)){
				//TO ENSURE SAME PIECE IS SHARED IN MULTI-QUE CASES
				Piece piece = disseminatedPiecesToCompete.get((int)p.pieceIndex); 
				if(piece == null){
					piece = p;
				}
				List<ManagedConnection> lMC = pieceToClients.get((int)piece.pieceIndex);
				if(lMC==null){
					lMC=new ArrayList<ManagedConnection>();
				}
				lMC.add(mc);
				pieceToClients.put(piece, lMC);
				cw.queued.add(piece);
				cw.blockLeft.addAll(piece.getAllBlocksLeft());
				itor.remove();
				disseminatedPiecesToCompete.put((int)p.pieceIndex, p);
				
			}
		}
	}
	//TODO: enqueue function for pieces
	public Set<Piece> currentQueue(){
		return currentQueue;
	}
	//TODO: completion stuff.
	/**
	 * Pulls all the read in blocks.
	 * On completion cancels any mismatched sections.
	 */
	public long readFromConnection(ManagedConnection mc,BitMap b){
		List<Response> rlist = mc.getPeerResponseBlocks();
		long bytes = 0;
		if(rlist!=null){
			for(Response r : rlist){
				bytes+=r.block.length;
				if(disseminatedPiecesToCompete.containsKey(r.index)){
//					System.out.println("working towards complete!");
					if(disseminatedPiecesToCompete.get(r.index).addData(r.begin, r.block)){
						//complete
						System.out.println("Complete! "+r.index);
						Piece p = disseminatedPiecesToCompete.remove(r.index);
						recentlyCompleted.add(p);
						dequeuePiece(p);
					}
				}else{
					//TODO: check if we need piece?
					if(!otherPiecesGettingComplete.containsKey(r.index)){
						otherPiecesGettingComplete.put(r.index, b.createPiece(r.index));
					}
					
					if(otherPiecesGettingComplete.get(r.index).addData(r.begin, r.block)){
						System.out.println("Complete! -Unown- "+r.index);
						recentlyCompleted.add(otherPiecesGettingComplete.remove(r.index));
					}
				}
			}
		}
		return bytes;
	}
	
	public void initializeConnection(ManagedConnection mc){
		if(clientToPieceSet.containsKey(mc)){
			throw new RuntimeException("Incorrect use. MC already initialized");
		}
		ConnectionWork cw = new ConnectionWork();
		clientToPieceSet.put(mc,cw);
	}
	
	
	public List<Piece> recentlyCompletedPieces(){
		if(recentlyCompleted.size()==0){return null;}
		List<Piece> plist = recentlyCompleted;
		recentlyCompleted = new ArrayList<Piece>();
		return plist;
	}
	
	/**
	 * This dequeue's piece # from all clients
	 * actively working to complete.
	 * @param piece
	 */
	public void dequeuePiece(Piece p){
		List<ManagedConnection> mcs = pieceToClients.get(p);
		if(mcs !=null){
			for(ManagedConnection mc : mcs){
				ConnectionWork cw = clientToPieceSet.get(mc);
				cw.queued.remove(p);
				Iterator<Request> itor = cw.blockLeft.iterator();
				while(itor.hasNext()){
					Request r = itor.next();
					if(r.index==p.pieceIndex){
						itor.remove();
					}
				}
				mc.cancelPiece(p);
			}
		}
		pieceToClients.remove(p);
	}
	
	/**
	 * Returns the set of pieces that are
	 * actively being worked on by *one or more*
	 * connections
	 * @return
	 */
	public Set<Integer> getWorkingSet(){
		return disseminatedPiecesToCompete.keySet();
	}
	
	/**
	 * Returns a list of requests still
	 * waiting to be made on the connection.
	 * @param mc
	 * @return
	 */
	public List<Request> getBufferedRequests(ManagedConnection mc){
		return clientToPieceSet.get(mc).blockLeft;
	}
	
	public Piece[] getQueuedPieces(ManagedConnection mc){
		ConnectionWork cw = clientToPieceSet.get(mc);
		return cw.queued.toArray(new Piece[0]);
	}
	
	/**
	 * Cancels the piece for a specific given connection.
	 * @param mc
	 * @param p
	 */
	public void cancelPieceForConnection(ManagedConnection mc, int piece){
		Piece p = disseminatedPiecesToCompete.get(piece);
		if(p!=null){
			List<ManagedConnection> list = pieceToClients.get(p);
			list.remove(mc);
			if(list.size()==0){
				//ok remove this from every where.
				//This might be the only client that had the piece :-|
				pieceToClients.remove(p);
				disseminatedPiecesToCompete.remove(piece);
			}
			
			ConnectionWork cw = clientToPieceSet.get(mc);
			cw.queued.remove(p);
			Iterator<Request> itor = cw.blockLeft.iterator();
			while(itor.hasNext()){
				Request r = itor.next();
				if(r.index==p.pieceIndex){
					itor.remove();
				}
			}
			if(mc.getConnectionState()==ConnectionState.connected){
				mc.cancelPiece(p);
			}
			
		}
	}
	
}
