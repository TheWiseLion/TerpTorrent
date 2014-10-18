package Trackers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import NetBase.ManagedConnection;
import Primitives.MetaConnection;
import TorrentData.Torrent;
import Utils.Bencoding;
import Utils.Bencoding.Type;

/***
 * Implementation of DHT tracker.
 * Hey, now we can use magnet links!
 * 
 * periodic work is done with doWork()
 * We search till either we cant find any closer
 * 
 * This class is a bit bloated. Some of my worse work. :-|
 * Just read my docs. Most of this ends up being implemented 
 * and non-tested due to lack of Upnp behind this nat.
 * 
 * @author wiselion
 */
public class DHTTracker extends Tracker{
	///Sooo lame. :-/
	private class ID{
		public final int hash;
		public final byte[]id;
		public ID(byte[] id) {
			this.id = id;
			int h = 0;
			for(int i=0;i<id.length;i++){
				h+=Math.abs(id[i]);
			}
			this.hash =h;
		}
		
		@Override
		public boolean equals(Object o){
			if(o instanceof ID){
				ID i = (ID)o;
				return Arrays.equals(i.id,id);
			}
			return false;
		}
		@Override
		public int hashCode(){return hash;}
		@Override
		public String toString(){
			BigInteger big = new BigInteger(1,id);
			return String.format("%0" + (id.length << 1) + "X", big);
		}
	}
	
	
	//Node in DHT network
	private class Node{
		final ID nodeId;
		final int port;
		final InetAddress ip;
		long timeSinceLastRecv= System.currentTimeMillis();
		long timeSinceLastSent= 0;
		byte [] lastToken;
		int drops = 0;
		boolean gaveClients = false;
		boolean announcedInto = false;//announced our selves as a peer into this node.
		int in=1;
		int out=1;
		boolean recved =false;//Set first time.
		String lastQuery = "zz";
		public Node(ID nodeId, int port, InetAddress ip) {
			super();
			this.nodeId = nodeId;
			this.port = port;
			this.ip = ip;
		}
		@Override
		public String toString(){
			BigInteger big = new BigInteger(1,nodeId.id);
			return String.format("%0" + ( nodeId.id.length << 1) + "X", big);
		}
		
		/**
		 * Follows the "Rules of Speaking" i outlined
		 * in the docs.
		 * @param n
		 * @return
		 */
		private boolean canTalkToNode(){
			long now = System.currentTimeMillis();
			if(!recved){
				return (now - timeSinceLastSent)>(3*1000);
			}else if(gaveClients){
				return (now - timeSinceLastSent)>(15*60*1000);
			}else{
				return (now - timeSinceLastSent)>(2*60*1000);
			}
		}
		
	}
	private enum RType{ping,get_peers,find_node,announce_peer}
	private static class Packet{
		
		Bencoding b;
		Node n;
		public Packet(Bencoding b,Node n){
			this.b=b;
			this.n = n;
		}
	}
	
	private static class Request{
		RType rt;
		long timeCreated = System.currentTimeMillis();
		public Request(RType rt){
			this.rt = rt;
		}
	}
	
	private byte[] getEightClosest(final byte [] target){
		List<Node> nodes = new ArrayList<Node>();
		for(Node node : idToNode.values()){
			nodes.add(node);
		}
		Collections.sort(nodes, new Comparator<Node>() {
			@Override
			//smallest is closest.
			public int compare(Node o1, Node o2) {
				BigInteger a = kademlia(o1.nodeId.id, target);
				BigInteger b = kademlia(o2.nodeId.id, target);
				return a.compareTo(b);
			}
		});
		int size = nodes.size()>=8?8*26:nodes.size()*26;
		byte [] b = new byte[size];
		for(int i=0;(i<8 && i<nodes.size());i++){
			Node n0 = nodes.get(i);
			System.arraycopy(n0.nodeId.id, 0, b, i*26, 20);
			System.arraycopy(n0.ip.getAddress(), 0, b, i*26+20, 4);
			b[i*26+20+4] = (byte)((n0.port >> 8)&0xFF);
			b[i*26+20+5] = (byte)(n0.port & 0xFF);
		}
		return b;
	}
	
	
	
	
	Map<ID,Node> idToNode;
	Map<ID,Map<String,Request>> idToRequestsO;//out
	Queue<Packet> requests;
	Queue<Packet> responses;
	private long lastRoot = 0;
	private boolean havePeers =false;
	DatagramSocket clientSocket;
	private byte[] infoHash;
	Thread recv;
	ID id;
	int total =0;
	private List<Node> connectionCleaner = new ArrayList<Node>(0);
	private List<String> packetCleaner = new ArrayList<String>(0);
	private List<MetaConnection> potentialPeers = new ArrayList<MetaConnection>(); 
	private long startTime = System.currentTimeMillis();
	public DHTTracker(byte [] infoHash,byte [] PeerID) throws SocketException{
		this.infoHash = infoHash;
		clientSocket = new DatagramSocket();
		idToNode = new HashMap<ID,Node>();
		idToRequestsO = new HashMap<ID,Map<String,Request>>();
		requests = new ConcurrentLinkedQueue<Packet>();
		responses = new ConcurrentLinkedQueue<Packet>();
		id = new ID(PeerID);
		recv = new Thread(){
			DatagramPacket dp = new DatagramPacket(new byte[65000],65000);
			boolean on =true;
			@Override
			public void run(){
					while(on){
					try {
						clientSocket.receive(dp);
						byte [] d = new byte[dp.getLength()];
						System.arraycopy(dp.getData(), dp.getOffset(), d, 0, dp.getLength());
						Bencoding b = new Bencoding(d);
						ID i=null;
						if(b.dictionary.containsKey("r")&&b.dictionary.get("r").dictionary.containsKey("id")){
							i = new ID(b.dictionary.get("r").dictionary.get("id").byteString);
						}else if(b.dictionary.containsKey("a")&&b.dictionary.get("a").dictionary.containsKey("id")){
							i = new ID(b.dictionary.get("a").dictionary.get("id").byteString);
						}else{
							System.out.println("Invalid in message");
							return;
						}
						
						Node n = new Node(i,dp.getPort(),dp.getAddress());
						String s = b.dictionary.get("y").getString();
						if(s.equals("e")){
							System.out.println("Got error");
							return;
						}else if(s.equals("r")){
							System.out.println("Response from "+n);
							responses.add(new Packet(b,n));
						}else if(s.equals("a")){
							System.out.println("Request from "+n);
							requests.add(new Packet(b,n));
						}else{
							continue;//something went wrong.
						}
						
						Thread.sleep(10);//Just for good measure... recv should block
					} catch (IOException e) {
						e.printStackTrace();
					}catch (RuntimeException r){
						System.out.println("Got packet. Couldnt decode bencoding");
					} catch (InterruptedException e) {
						e.printStackTrace();
						on =false;
					}
					
				}
			}
		};
		recv.start();
		
	}

	
	
	@Override
	protected void work(){
		try{
			//dont call more then once every 3 secs.
			if(idToNode.size()==0&&(System.currentTimeMillis()-lastRoot>3*1000)){
				//add root node?
				//send pings to roots
				/**
				 * Boot strapping lister:
				 * dht.transmissionbt.com
				 * router.utorrent.com
				 * router.bittorrent.com
				 * Man it feels like poping in 
				 * the primary root servers into a textfile....
				 */
				byte [] ping = constructPing(id, "ZZ");
				//boot strap nodes:
				DatagramPacket dp0 = new DatagramPacket(ping,ping.length,InetAddress.getByName("dht.transmissionbt.com"),6881);
				DatagramPacket dp1 = new DatagramPacket(ping,ping.length,InetAddress.getByName("router.utorrent.com"),6881);
				DatagramPacket dp2 = new DatagramPacket(ping,ping.length,InetAddress.getByName("router.bittorrent.com"),6881);
				clientSocket.send(dp0);
				clientSocket.send(dp1);
				clientSocket.send(dp2);
				lastRoot = System.currentTimeMillis();
			}
		
			//respond to requests
			processRequests();
			
			//filter through responses
			processResponses();
			
			//Search or send requests
			Node closest=null; //MINIMIZE kademlia
			BigInteger i = new BigInteger(1,new byte[]{-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1});//large.
			//TODO: Switch to N closest.
			for(Node n : idToNode.values()){
				//TODO: prefer nodes that we can talk to (now.)!!
				if(!n.canTalkToNode()){
					continue;
				}
				BigInteger diff = kademlia(infoHash,n.nodeId.id);
				if(diff.compareTo(i)<=0){
					closest = n;
					i = diff; 
				}
				
			}
			
			if(closest!=null && closest.canTalkToNode()){//no more then 1 per 3 seconds
				//query for nodes with closer hash
				closest.lastQuery = nextFromLast(closest.lastQuery);
				byte [] findNode = get_peers(id,closest.lastQuery,infoHash);
				System.out.println("Asking: "+closest);
				DatagramPacket dp0 = new DatagramPacket(findNode,findNode.length,closest.ip,closest.port);
				clientSocket.send(dp0);
				closest.timeSinceLastSent = System.currentTimeMillis();
				Map<String,Request> rMap = idToRequestsO.get(closest.nodeId);
				if(rMap==null){
					rMap = new HashMap<String,Request>();
				}
				rMap.put(closest.lastQuery,new Request(RType.get_peers));
				idToRequestsO.put(closest.nodeId,rMap);
				closest.drops++;
			}
			
			
			
			
			 ////////////////// MAINTENANCE //////////////////
			
			
			connectionCleaner.clear();
			for(Node n: idToNode.values()){
				if(n.drops>7||System.currentTimeMillis()-n.timeSinceLastRecv>15*60*1000){
					connectionCleaner.add(n);
				}
			}
			for(Node n: connectionCleaner){
				System.out.println("Dropping: "+n.toString());
				idToNode.remove(n.nodeId);
				idToRequestsO.remove(n.nodeId);
			}
			connectionCleaner.clear();
			//Keep N closest. (maybe 40?)
			if(idToNode.size()>40){
				List<Node> allNodes = new ArrayList<Node>(idToNode.values());
				Collections.sort(allNodes, new Comparator<Node>() {
					@Override
					//smallest is closest: TODO: test.
					public int compare(Node o1, Node o2) {
						BigInteger a = kademlia(o1.nodeId.id, infoHash);
						BigInteger b = kademlia(o2.nodeId.id, infoHash);
						return a.compareTo(b);
					}
				});
				for(int z =40;z<allNodes.size();z++){
					connectionCleaner.add(allNodes.get(z));
				}
			}
			
			for(Node n: connectionCleaner){
				if(n.gaveClients){
					System.out.println("Tried to drop a special one! "+ n);
					continue; //Might end up asking him for more E_O
				}
				System.out.println("Dropping: "+n.toString()+ " lower then top 40.");
				idToNode.remove(n.nodeId);
				idToRequestsO.remove(n.nodeId);
			}
			
			
			// for each active request (clean up those drops) 30 second timeout.
			for (ID conId : idToRequestsO.keySet()) {
				Map<String, Request> rMap = idToRequestsO.get(conId);
				if (rMap != null) {
					packetCleaner.clear();
					for (String s : rMap.keySet()) {
						if (System.currentTimeMillis() - rMap.get(s).timeCreated > 30 * 1000) {
							System.out.println("Dropping Reqest " + s + " on connection "+ conId.toString());
							packetCleaner.add(s);
						}
					}
					for (String s : packetCleaner) {
						rMap.remove(s);
					}
				}
			}
			
			 //////////////// END-MAINTENANCE //////////////////
			
			
		}catch(IOException e){
			//Oh how the great have fallen.
			this.workingTracker = false;
			this.error ="Uknown IOException. Probably cant find host. ---->"+e.getMessage();
		}
		
	}
	
	
	
	
	//unsigned integer == distance(A,B) = |A xor B|
	BigInteger kademlia(byte [] a,byte [] b){
		if(a.length!=b.length){
			throw new RuntimeException("Array's must be same length");
		}
		byte[] c = new byte[a.length];
		for(int i=0;i<a.length;i++){
			c[i]=(byte) (a[i]^b[i]);
		}
		return new BigInteger(1,c);
	}

	/**
	 * @param id
	 * @param q
	 * @param r - whether or not its a request. true if request
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private Bencoding msgBase(ID id,String q,boolean r) throws UnsupportedEncodingException{
		Bencoding b = new Bencoding();
		b.type = Type.Dictionary;
		b.dictionary=new HashMap<String, Bencoding>();
		Bencoding a = new Bencoding();
		a.type = Type.Dictionary;a.dictionary=new HashMap<String,Bencoding>();
		Bencoding i = new Bencoding();
		i.byteString=id.id;
		i.type=Type.String;
		
		
		b.dictionary.put("t",new Bencoding(q));
		if(!r){
			b.dictionary.put("y",new Bencoding("q"));
			b.dictionary.put("a", a);
		}else{
			b.dictionary.put("y",new Bencoding("r"));
			b.dictionary.put("r", a);
		}
		a.dictionary.put("id", i);
		
		return b;
	}

	
	byte[] respondPing(ID n,String q) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,true);
		return b.toByteArray();
	}
	
	byte[] constructPing(ID id,String q) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,false);
		b.dictionary.put("q",new Bencoding("ping"));
		return b.toByteArray();
	}
	
	byte [] constructNodeResponse(ID id,String q, byte[] nodes) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,true);
		b.dictionary.get("r").dictionary.put("nodes", Bencoding.cBS(nodes));
		return b.toByteArray();
	}
	
	byte [] find_node(ID id,String q,byte [] target) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,false);
		b.dictionary.put("q",new Bencoding("find_node"));
		Bencoding btarget = new Bencoding();
		btarget.byteString=target;
		btarget.type=Type.String;
		b.dictionary.get("a").dictionary.put("target", btarget);
		return b.toByteArray();
	}
	
	byte[] constructPeersResponseN(ID id, String q, byte[] nodes, byte[] token) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,true);
		b.dictionary.get("r").dictionary.put("token", Bencoding.cBS(token));
		b.dictionary.get("r").dictionary.put("nodes", Bencoding.cBS(nodes));
		return b.toByteArray();
	}
	
	byte[] constructPeerResponseP(ID id,String q, Bencoding peers,byte [] token) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,true);
		b.dictionary.get("r").dictionary.put("token", Bencoding.cBS(token));
		b.dictionary.get("r").dictionary.put("values",peers);
		return b.toByteArray();
	}
	
	//The return value for a query for peers includes an opaque value known as the "token."
	byte [] get_peers(ID id,String q,byte [] infoHash) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,false);
		b.dictionary.put("q",new Bencoding("get_peers"));
		Bencoding btarget = new Bencoding();
		btarget.byteString=infoHash;
		btarget.type=Type.String;
		b.dictionary.get("a").dictionary.put("info_hash", btarget);
		System.out.println(new String(b.toByteArray(),"UTF-8"));
		return b.toByteArray();
	}
	byte [] constructAnnounceResponse(ID id, String q) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,true);
		return b.toByteArray();
	}
		
	byte [] announce_peer(ID id,String q,byte [] infoHash,byte[] token,boolean isport,int port) throws UnsupportedEncodingException{
		Bencoding b = msgBase(id, q,false);
		b.dictionary.put("q",new Bencoding("announce_peer"));
		Bencoding btarget = new Bencoding();
		btarget.byteString=infoHash;
		btarget.type=Type.String;
		b.dictionary.get("a").dictionary.put("info_hash", btarget);
		//Token
		Bencoding btoken = new Bencoding();
		btoken.byteString=token;
		btoken.type=Type.String;
		b.dictionary.get("a").dictionary.put("token", btoken);
		//Implied Port
		b.dictionary.get("a").dictionary.put("port",new Bencoding(""+port));
		if(isport){
			b.dictionary.get("a").dictionary.put("implied_port",new Bencoding("1"));
		}else{
			b.dictionary.get("a").dictionary.put("implied_port",new Bencoding("0"));
		}
		return b.toByteArray();
	}
	
	private void placeNodes(byte [] nodes) throws UnknownHostException{
		if(nodes.length%26 != 0){
			throw new RuntimeException("Nodes dont match");
		}
		int count=0;
		for(int i=0;i<nodes.length;i+=26){
			ID id =new ID(Arrays.copyOfRange(nodes, i, i+20));
			InetAddress ip = InetAddress.getByAddress(Arrays.copyOfRange(nodes, i+20, i+24));
			int port = ((nodes[i+24]&0xFF) << 8 | (nodes[i+24+1]&0xFF));
			if(!idToNode.containsKey(id)){
				Node n = new Node(id,port,ip);
				idToNode.put(id, n);
				count++;
			}
		}
		System.out.println("Added "+count+" nodes");
	}
	
	private void getPeers(byte [] peers) throws UnknownHostException{
		for(int i=0;i<peers.length/6;i++){
			byte []addr = new byte[4];
			System.arraycopy(peers, i*6, addr, 0, 4);
			InetAddress ip = InetAddress.getByAddress(addr);
			int port =((peers[i*6+4]&0xFF) << 8 | (peers[i*6+5]&0xFF));
			System.out.println("Peer: "+ip.getHostAddress()+":"+port);
			if(!Arrays.equals(ip.getAddress(),new byte[]{0,0,0,0})){//This is a baddie telling me to connect to local!
				potentialPeers.add(new MetaConnection(ip,port));
			}
			total++;
		}
	}
	
	//loops from aa to zz...
	private String nextFromLast(String last){
		//aa to zz
		char c0 = (char) (last.charAt(0));
		char c1 = (char) (last.charAt(0)+1);
		if(c1>'z'){
			c1='a';
			c0+=1;
		}
		if(c0>'z'){
			c0='a';
		}
		return new String(new char[]{c0,c1});
	}

	@Override
	protected long getWaitMS() {
		if(havePeers){//Its not as important once we've found some peers.
			return 20000;
		}
		return  1000;
	}

	@Override
	public void update(Torrent t) {
		//push out our list.
		for(MetaConnection mc : potentialPeers){
			t.addConnection(mc.toManagedConnection(t));
		}
		potentialPeers.clear();
	}
	
	public List<MetaConnection> connections(){
		List<MetaConnection> mcs = potentialPeers;
		potentialPeers = new ArrayList<MetaConnection>();
		return mcs;
	}

	@Override
	public void close(Torrent t) {
		//shut down. close port.
		recv.stop();
	}
	
	private void processResponses() throws IOException{
		while(!responses.isEmpty()){
			Packet p = responses.poll();
			if(idToRequestsO.containsKey(p.n.nodeId)){
				Node actual = idToNode.get(p.n.nodeId);
				actual.in++;
				actual.recved = true;
				Map<String,Request> rMap = idToRequestsO.get(actual.nodeId);
				String s =p.b.dictionary.get("t").getString();
				Request rt = rMap.remove(s);
				actual.drops = 0;//reset drops
				if(rt==null){
					System.out.println("Got response but no matching request?");
					continue;
				}
				if(rt.rt == RType.ping){
					actual.timeSinceLastRecv = System.currentTimeMillis();
				}else if(rt.rt == RType.get_peers){
					Bencoding r = p.b.dictionary.get("r");
					if(r.dictionary.containsKey("values")){
						actual.gaveClients =true;
						for(Bencoding b : r.dictionary.get("values").list){
							getPeers(b.byteString);
						}
						havePeers = true;
					}
					if(r.dictionary.containsKey("nodes")){
						placeNodes(r.dictionary.get("nodes").byteString);
					}
					
					if(r.dictionary.containsKey("token")){
						actual.lastToken = r.dictionary.get("token").byteString;
					}
					//TODO:
//					actual.lastQuery = nextFromLast(actual.lastQuery);
//					byte [] data = announce_peer(id,actual.lastQuery, infoHash, actual.lastToken, false, -1);
//					DatagramPacket dp = new DatagramPacket(data,data.length,actual.ip,actual.port);
//					clientSocket.send(dp);
					
					
				}else if(rt.rt == RType.find_node){
					actual.recved = true;
					Bencoding nodes = p.b.dictionary.get("nodes");
					placeNodes(nodes.byteString);
				}else if(rt.rt == RType.announce_peer){
					actual.recved = true;
					actual.announcedInto=true;
					//dont really have to do much..
				}
				
			}else{
				//We use pings to add our root's....
				if(!idToNode.containsKey(p.n.nodeId)){
					System.out.println("Got new node!");
					idToNode.put(p.n.nodeId, p.n);
				}else{
					System.out.println("Couldnt match to request. Dropped packet maybe?");
				}
				p.n.timeSinceLastRecv=System.currentTimeMillis();
			}
		}
	}
	
	private void processRequests() throws IOException{
		while(!requests.isEmpty()){
			Packet p = requests.poll();
			Node n = idToNode.get(p.n.nodeId);
			if(n!=null){
				//Immedate respond.
				n.out++;
				if(n.in>100){
					idToNode.remove(n);
					idToRequestsO.remove(n.nodeId);
				}
				//For now this "Tracker" doesn't host peers (we dont keep a peer list).
				//we always return nodes. plain and simple.
				String s = p.b.dictionary.get("q").getString();
				if(!p.b.dictionary.containsKey("t")){
					continue;//bad!
				}
				Map<String,Bencoding> args = p.b.dictionary.get("a").dictionary;
				String t = p.b.dictionary.get("t").getString();
				
				byte [] d;
				if(s.equals("ping")){
					d = constructPing(id,t);
				}else if(s.equals("find_nodes")){
					if(!args.containsKey("target")){
						continue;//bad!
					}
					final byte []  target = args.get("target").byteString;
					//get 8 closest nodes
					byte [] nodes = getEightClosest(target);
					d = constructNodeResponse(id, t, nodes);//Were not really using token :-{
				}else if(s.equals("get_peers")){
					if(!args.containsKey("info_hash")){
						continue;//bad!
					}
					final byte []  target = args.get("info_hash").byteString;
					byte [] nodes = getEightClosest(target);
					d = constructPeersResponseN(id, t, nodes, new byte[]{65,65,65,65});//Were not really using token :-{
					
				}else if(s.equals("announce_peer")){
					//say ok, but were actually doing nothing.
					d = constructAnnounceResponse(id,t);
				}else{
					//respond with ping?
					d = constructPing(id,t);
				}
				DatagramPacket dp0= new DatagramPacket(d, d.length,n.ip,n.port);
				clientSocket.send(dp0);
			}
			
		}
	}
	
	/**
	 * Its possible to get lost.
	 * This restarts us to root.
	 */
	public void restart(){
		idToNode.clear();
		idToRequestsO.clear();
		requests.clear();
		responses.clear();
	}
	
	public long timeSinceStart(){
		return System.currentTimeMillis() -startTime;
	}
	
	public void setAggrsive(){
		this.havePeers =false;
	}
	
	@Override
	public String toString(){
		return "DHT-"+id.toString();
	}
	
	
	////////////////////////////// TODO: remove //////////////////////////////
	public static byte[] hexStringToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	public static void main(String [] args) throws InterruptedException, NoSuchAlgorithmException, IOException{
//		Torrent t = TorrentParser.parseTorrentFile("8mb_lex.torrent");
		
		byte [] b = hexStringToByteArray("00599b501d8713640be4f481433dd0848a592ef3");
		System.out.println(b.length);
		BigInteger big = new BigInteger(1,b);
		System.out.println(String.format("%0" + (b.length << 1) + "X", big));
		
		DHTTracker dht=new DHTTracker(b,Torrent.generateSessionKey(20).getBytes("UTF-8"));
		while(true){
			dht.doWork();
			Thread.sleep(10);
		}
	}



	@Override
	public int totalPeersObtained() {
		// TODO Auto-generated method stub
		return total;
	}

	
}
