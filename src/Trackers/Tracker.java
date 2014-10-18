package Trackers;

import java.net.UnknownHostException;

import TorrentData.Torrent;

/**
 * Generic tracker class.
 * 3 types of trackers:
 * UDP
 * DHT
 * HTTP
 * 
 * @author wiselion
 */
public abstract class Tracker {
	//wait time
	protected String error;
	protected boolean workingTracker = true;
	private long time = 0;
	private final void updateTimer(){
		time = System.currentTimeMillis();
	}
	
	private final long getTimeSinceWork(){
		return System.currentTimeMillis()-time;
	}
	
	/**
	 * Internal call do find out if tracker can 
	 * run.
	 * @return
	 */
	protected abstract long getWaitMS();
	
	/**
	 * General call to tracker. May block, but usually not for too long.
	 */
	protected abstract void work();
	
	public void doWork(){
//		System.out.println("Diff time: "+getTimeSinceWork());
		if(getTimeSinceWork()>getWaitMS()&&workingTracker){
			work();
			updateTimer();
		}
		
	}
	
	/**
	 * Adds newly found peers to the torrent.
	 * @param t
	 */
	public abstract void update(Torrent t);
	
	/***
	 * Called when torrent is no longer talking to 
	 * peers
	 * @param t
	 */
	public abstract void close(Torrent t);
	
	/***
	 * Called when torrent is completed downloading
	 * @param t
	 */
	public void complete(Torrent t){}
	public void initialize(Torrent t){}
	
	public boolean isWorking(){
		return workingTracker;
	}
	
	/**
	 * Error message will be set if 
	 * isWorking is false
	 * @return
	 */
	public String getError(){
		return error;
	}
	
	/**
	 * Makes UDP/HTTP/DHT-Tracker as url indicates.
	 * @param url
	 * @return
	 * @throws UnknownHostException 
	 */
	public static Tracker makeTracker(String url) throws UnknownHostException{
		if(url.startsWith("udp")){
			return new UDPTracker(url);
		}else if(url.startsWith("magnet")){
			//doesnt matter we make regardless.
			return null;
		}else if(url.startsWith("http:")){
			return new HTTPTracker(url);
		}else{
			return null;
		}
	}
	
	public abstract int totalPeersObtained();
	
}
