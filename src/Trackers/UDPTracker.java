package Trackers;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Random;

import TorrentData.Torrent;
import Trackers.HTTPTracker.Event;
import Utils.ByteUtils;

public class UDPTracker extends Tracker{
	public static enum UDPAction {
		CONNECT(0),
		ANNOUNCE(1),
		SCRAPE(2),
		ERROR(3);
		int value;
		UDPAction(int value){
			this.value = value;
		}
	}
	URL url;
	String strUrl;
	int port;
	InetAddress address;
	String id;
	boolean completeOnce = false;
	Torrent t;
	int total = 0;
	public UDPTracker(String url) throws UnknownHostException {
		if (url.substring(0, 3).equals("udp")) {
			String[] token = url.split("/");
			System.out.println(token[2]);
			if (token[2].contains(":")) {
				token = token[2].split(":");
				address = InetAddress.getByName(token[0]);
				port = Integer.parseInt(token[1]);
			} else {
				address = InetAddress.getByName(token[2]);
				port = 80;
			}
		}
	}
	
	
	
	
	public void getUPDTrackerResult(Torrent t,Event e){
		DatagramSocket sock = null;
		try {
			sock = new DatagramSocket();
			sock.setSoTimeout(3*1000);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//Start connection message
		DatagramPacket sendPacket  = null, recvPacket = null;
		byte[] sendBuffer = null, recvBuffer = null;
		long conID = 0x41727101980l;
		Random r = new Random();
		int transactionID = r.nextInt();
		recvBuffer = new byte[65508];
		recvPacket = new DatagramPacket(recvBuffer,recvBuffer.length);
		ByteBuffer message = ByteBuffer.allocate(98);
		sock.connect(address,port);
		
		message.putLong(conID);
		message.putInt(UDPAction.CONNECT.value);	
		message.putInt(transactionID);
		sendBuffer = message.array();
		sendPacket = new DatagramPacket(sendBuffer,sendBuffer.length,address,port);
		byte[] reponse = null;
		try {
			sock.send(sendPacket);
			sock.receive(recvPacket);
			reponse = recvPacket.getData();
			if(reponse!=null){
				 // Start Announce 
				 int actionIdFromResponse = ByteUtils.readInt(reponse, 0);
				 int transactionIdFromResponse = ByteUtils.readInt(reponse, 4);
				 long connectionIdFromResponse = ByteUtils.readUnsignedInt(reponse, 8);
				 transactionID = r.nextInt();
				 int event = 0;
					if(e == Event.started){
						event =2;
					}else if(e == Event.stopped){
						event =3;
					}else if(e == Event.completed){
						event =1;
					}
				int peersWanted = 20;
				 sendBuffer = getAnnounceInput(conID,transactionID,t.getInfoHash(),t.getPeerID(),t.getDownloaded(),t.totalBytes,0,event,peersWanted,(short)sock.getPort());
				sendPacket = new DatagramPacket(sendBuffer,sendBuffer.length,address,port);
				sock.send(sendPacket);
				sock.receive(recvPacket);
				reponse = recvPacket.getData();
				if(reponse!=null){
					completeOnce = true;
					if(reponse.length >= 20){
					int recTransId = ByteUtils.readInt(reponse, 4);
					int interval = ByteUtils.readInt(reponse, 8);
					int leechers = ByteUtils.readInt(reponse, 12);
					int seeders = ByteUtils.readInt(reponse, 16);
					short peerPort=-1;
					for(int i= 0;i<(reponse.length-20)/6&&peerPort!=0;i++){
						byte[] rawIp = new byte[4];
						System.arraycopy(reponse, 20+i*6, rawIp, 0, 4);
						InetAddress ip = InetAddress.getByAddress(rawIp); 
						peerPort = ByteUtils.readShort(reponse, 24+6*i);
						if(peerPort!=0){
							total++;
							t.addPeer(ip,peerPort, null);
							System.out.println("Peer: "+ip.getHostAddress()+":"+port);
						}
					}
					}else{
						System.err.println("ERROR");
					}
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private byte[] getAnnounceInput(long conId, int transId,byte[] infoHash, byte[] peerId,long downloaded,long left, long uploaded, int event,int numWanted, short port){
		ByteBuffer message = ByteBuffer.allocate(98);
		message.putLong(conId);
		message.putInt(UDPAction.ANNOUNCE.value);
		message.putInt(transId);
		message.put(infoHash);
		message.put(peerId);
		message.putLong(downloaded);
		message.putLong(left);
		message.putLong(uploaded);
		message.putInt(event);
		message.putInt(0);
		message.putInt(0);
		message.putInt(20);
		message.putShort(port);
		return message.array();
	}




	
	
	@Override
	protected long getWaitMS() {
		return 1000;
	}
	@Override
	public void initialize(Torrent t){
		this.t = t;
	}
	




	@Override
	protected void work() {
		//Do work till we get initial connection.
		if(!completeOnce){
			getUPDTrackerResult(t,Event.started);
		}
	}
	
	@Override
	public void complete(Torrent t){
		getUPDTrackerResult(t,Event.completed);
	}
	
	




	@Override
	public void update(Torrent t) {}//meh.
	




	@Override
	public void close(Torrent t) {
		getUPDTrackerResult(t,Event.stopped);
	}

	@Override
	public int totalPeersObtained() {
		return total;
	}
}
