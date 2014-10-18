package TorrentData;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URLDecoder;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import NetBase.ManagedConnection.ConnectionState;
import Primitives.MetaConnection;
import Primitives.MetaData;
import Trackers.DHTTracker;
import Utils.TorrentParser;

/**
 * Specail torrent class made
 * for magnet links (we dont start with .torrent file)
 * 
 * Magnet link becomes a torrent file when
 * we find a client to send us the meta data.
 * (piece size and file sizes, etc)  
 * 
 * @author wiselion
 */
public class MagnetLink extends MetaTorrent{
	/**
	 * Pulled from a stack over flow.. dont remember which
	 * @param s
	 * @return
	 */
	private static byte[] hexStringToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	/**
	 * Pulled from 
	 * http://stackoverflow.com/questions/13592236/parse-the-uri-string-into-name-value-collection-in-java
	 * 
	 * AND then promptly edited.
	 * @param url
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static Map<String, String> splitQuery(String s) throws UnsupportedEncodingException {
	    Map<String, String> query_pairs = new LinkedHashMap<String, String>();
	    String query =s;
	    String[] pairs = query.split("&");
	    for (String pair : pairs) {
	        int idx = pair.indexOf("=");
	        if(!query_pairs.containsKey(URLDecoder.decode(pair.substring(0, idx), "UTF-8")))//keep first of key..
	        query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
	    }
	    return query_pairs;
	}
	
	private boolean complete = false;
	
	public String udpTracker=null;
	public String httpTracker=null;
	public DHTTracker DHTtracker=null;
	
	public Set<MetaConnection> connections; //We looking for our .torrent, gatta talk to someone!
	
	private MagnetLink(byte [] info,byte[] p,String name,String u,String http) throws SocketException{
		super(info,p,new MetaData());
		this.name = name;
		DHTtracker = new DHTTracker(hashInfo,peerID);//binds to a socket.
		connections = new HashSet<MetaConnection>();
		status = "Looking for connections";
	}
	
	/**
	 * Takes either a hashinfo or magnet link
	 * @param uri
	 * @throws MalformedURLException 
	 * @throws UnsupportedEncodingException 
	 * @throws SocketException 
	 */
	public static MagnetLink createFromURI(String uri) throws MalformedURLException, UnsupportedEncodingException, SocketException{
		
		byte []hashInfo;
		byte []peerID;
		String name;
		String udpTracker = null;
		String httpTracker = null;
		if(uri.startsWith("magnet:?")){
			//build search:
			//TODO: some extra trackers will be lost.
			Map<String,String> query =splitQuery(uri.substring("magnet:?".length()));
			if(!query.containsKey("xt")){
				throw new RuntimeException("invalid magnet link");
			}
			String hash = query.get("xt");
			
			if(hash.startsWith("urn:btih:")){
				hash = hash.substring("urn:btih:".length());
			}else{
				throw new RuntimeException("invalid magnet format. unknown hash format");
			}
			
			hashInfo = hexStringToByteArray(hash);
			//Check for the extra's:
			if(query.containsKey("dn")){
				name = query.get("dn");
			}else{
				name = hash;
			}
			//TODO: multiple
			if(query.containsKey("tr")){
				String tracker = query.get("tr");
				if(tracker.startsWith("udp")){
					udpTracker = tracker.substring(3);
				}else if(tracker.startsWith("http:")){
					httpTracker = tracker.substring(0);
				}else{
					//we dont support https...
				}
			}
			
		}else{
			//better be base32 hashinfo
			name = uri;
			hashInfo = hexStringToByteArray(uri);
			if(uri.length()!=40){
				throw new RuntimeException("NOT VALID HASHINFO, must be base 32");
			}
		}
		
		if(hashInfo.length!=20){
			throw new RuntimeException("NOT VALID HASHINFO, must be base 32");
		}
		
		
		peerID = Torrent.generateSessionKey(20).getBytes("UTF-8");
		
		return new MagnetLink(hashInfo,peerID,name,udpTracker,httpTracker);
	}
	
	/***
	 * Nonblocking... 
	 * Will search DHT till meta is complete
	 * @throws IOException 
	 * @throws NoSuchAlgorithmException 
	 * 
	 */
	public void doWork() throws IOException, NoSuchAlgorithmException{
		Iterator<MetaConnection> itor = connections.iterator();
		while(itor.hasNext()){
			//Stand connection loop...
			MetaConnection mc = itor.next();
			if(mc.getConnectionState() == ConnectionState.closed){
				itor.remove();
				System.out.println("ENDED: "+mc);
			}else if(mc.getConnectionState() == ConnectionState.uninitialized){
				mc.blindInitialize(meta);
			}else if(mc.getConnectionState() == ConnectionState.connected){
				//push requests.
				status = "Peer's found. Searching for meta data";
				mc.setAmChoking(false);
				mc.setAmInterested(true);
				mc.doWork(this);
//				if(mc.supportsMetaMetaRequest()){
//					if(mc.getActiveRequests().length<2){//pretty slow but w/e
//						
//						int i = meta.getOpenPiece();
//						if(i!=-1){
//							mc.sendMetaMetaRequest(i);
//						}
////						System.out.println("Asking for piece! "+i+" from "+mc);
//					}
//				}
			}
		}
		DHTtracker.doWork();
		
		
		
		List<MetaConnection> cms = DHTtracker.connections();
		if(cms!=null){
			connections.addAll(cms);
		}
		
		if(connections.size()==0){
			DHTtracker.setAggrsive();
		}
		
		
		if(meta.isComplete()){
			//do something?
			if(meta.shaMatch(hashInfo)){
				complete =true;
			}
			
		}
		
	}
	
	public boolean isComplete(){
		return complete;
	}
	
	public String getStatus(){
		return status;
	}
	public Torrent construct(){
		if(complete){
			//TODO construct
		}
		return null;
	}
	
	public void close(){
		DHTtracker.close(null);
	}
	
	public static void main(String [] args) throws IOException, InterruptedException, NoSuchAlgorithmException{
		Torrent t  = TorrentParser.parseTorrentFile("ubuntu.torrent");
		BigInteger big = new BigInteger(1,t.hashInfo);
		MagnetLink ml = createFromURI(String.format("%0" + (t.hashInfo.length << 1) + "X", big));
//		HTTPTracker ht = new HTTPTracker(t.tracker);
		
		while(!ml.isComplete()){
			ml.doWork();
			Thread.sleep(10);
		}
		
	
	}

}
