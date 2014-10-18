package TorrentData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import NetBase.ManagedConnection;
import Primitives.DownloadFile;
import Primitives.MetaData;
import Trackers.DHTTracker;
import Trackers.Tracker;
import Utils.Bencoding;
import Utils.TorrentParser;


/****
 * Base class. Contains all the information of the torrent
 * @author wiselion
 */
public class Torrent extends MetaTorrent{
	public static int STANDARD_CACHE_SIZE = 1024*1024*20;//20MB
	private Set<ManagedConnection> peerList = new HashSet<ManagedConnection>();//TODO: keep only peers with valid connections?
	public final PieceManager pm;
	public final int numFiles;
	public final int pieceLength;
	public final DownloadFile[] files;
	public long totalBytes;
	public final String name;
	public final Bencoding pieceHash;
	private long downloaded;
	private long uploaded;
	private boolean haveFiles;
	private long recentDownRate;
	private long recentUpRate;
	private List<Tracker> trackers;
	private long left;//TODO: associate with files.
	public int uPnP_Port = 1010;//TODO
	private File f;
	
	
	public Torrent(String name,int pieceLength, DownloadFile[] files,
			long totalBytes, byte[] byteStringHashInfo,String urlEncodedHash,Bencoding pieceHash,MetaData md,List<Tracker> trackers,String file) throws IOException {
		super(byteStringHashInfo,generateSessionKey(20).getBytes("UTF-8"),md);
		this.numFiles = files.length;
		f = new File(file);
		this.pieceLength = pieceLength;
		this.files = files;
		this.totalBytes = totalBytes;
		this.name = name;
		downloaded=0;
		uploaded=0;
		left = totalBytes;
		this.pieceHash=pieceHash;
		try {
			pm = new PieceManager(files,STANDARD_CACHE_SIZE,pieceLength,totalBytes,pieceHash);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("IO-Problems on piecemanager init.");
		}
		for(DownloadFile d:files){
			d.initialize(pieceLength, pm.bitmap);
		}
		this.status = "Checking files";
		pm.checkFiles();
		this.status = "Getting Peers";
		haveFiles = true;
		this.trackers = new ArrayList<Tracker>();
		for(Tracker t : trackers){
			this.trackers.add(t);
		}
		this.trackers.add(new DHTTracker(hashInfo,peerID));//We're always in DHT!
	}
	
	public static String generateSessionKey(int length){
		String alphabet =new String("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"); 
		int n = alphabet.length(); 
		String result = new String(); 
		Random r = new Random(); 
		for (int i=0; i<length; i++) 
		    result = result + alphabet.charAt(r.nextInt(n));
	
		return result;
	}
	
	public void reload() throws NoSuchAlgorithmException, IOException{
		Torrent t = TorrentParser.parseTorrentFile(f.getAbsolutePath());
		this.trackers = t.trackers;
		int i=0;
		for(DownloadFile f : t.files){
			this.files[i++]=f;
		}
	}
	
	@Override
	public String toString(){
		String s="";
		for(Tracker t: trackers){
			s+=t.toString()+",";
		}
		
		
		
		return name+"\nfiles: "+numFiles+"\nSize: "+totalBytes+"\nTackers: "+s+"\nPeers: "+peerList.size()+"\n";
	}
	
	public void addPeer(InetAddress inet, int port,byte[] id){
		ManagedConnection mc = new ManagedConnection(inet,port);
		if(id!=null){
			mc.setPreConnectionPeerID(id);
		}
		if(!peerList.contains(mc)&&peerList.size()<50){
			peerList.add(mc);
		}
	}
	
	public void addConnection(ManagedConnection mc){
		if(!peerList.contains(mc)){
			peerList.add(mc);
		}
	}
	
	public byte[] getInfoHash(){
		return hashInfo.clone();
	}
	
	public byte[] getPeerID(){
		return peerID;
	}
	
	public long getDownloaded(){
		return downloaded;
	}
	
	public long getUploaded(){
		return uploaded;
	}
	
	public void addDownloaded(long bytes){
		downloaded+=bytes;
	}
	
	public void addUploaded(long bytes){
		downloaded+=bytes;
	}
	
	public long getLeft(){
		left =(totalBytes-pm.getCompletedBytes());
		left = left>0?left:0;
		return left;
	}



	public Set<ManagedConnection> getPeers() {
		return peerList;
	}

	public void setRecentDown(long dl){
		this.recentDownRate = dl;
	}
	
	public void setRecentUp(long up){
		this.recentUpRate = up;
	}
	
	public long getRecentDownRate(){
		return recentDownRate;
	}

	public long getRecentUpRate(){
		return recentUpRate;
	}
	
	public Tracker[] getTrackers(){
		return trackers.toArray(new Tracker[0]);
	}
	
	public File getFile(){
		return f;
	}
	
	public boolean equals(Object o){
		if(o instanceof Torrent){
			Torrent t = (Torrent)o;
			return Arrays.equals(t.hashInfo, hashInfo);
		}
		return false;
	}
	
}
