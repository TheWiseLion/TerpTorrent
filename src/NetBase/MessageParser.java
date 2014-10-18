package NetBase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import Utils.ByteUtils;

/**
 * Utility class that reads messages in non-blocking mode.
 * Also contains static functions to speak the torrent proto.
 * 
 * 
 * NOT SYNCHRONIZABLE!! So dont do it.
 * 
 */
public class MessageParser {
	/*
	 * 
	 * All of the remaining messages in the protocol take the form of <length prefix><message ID><payload>. 
	 * The length prefix is a four byte big-endian value. 
	 * The message ID is a single decimal byte. The payload is message dependent.
	 * 
	 */
	public static class HandShake{public final byte[] peerID, hashInfo, version, reserved;
		public HandShake(byte[] peerID, byte[] hashInfo, byte[] version,byte[] reserved) {
			this.peerID = peerID;this.hashInfo = hashInfo;this.version = version;this.reserved = reserved;
		}
	}
	public static class Response{
		public final int index,begin;
		public final byte[] block;
		public Response(int index, int begin, byte[] block) {this.index = index;this.begin = begin;this.block = block;}
		public Response(long index, long begin, byte[] block) {this.index = (int) index;this.begin = (int) begin;this.block = block;}
	}
	public static class Request{
		public final int index,begin,len;
		public final long creationTime = System.currentTimeMillis();
		public  long sentTime;
		boolean sent = false;
		public Request(int i,int b,int l){this.index=i;this.begin=b;this.len=l;}
		public Request(long i,long b,long l){this.index=(int) i;this.begin=(int) b;this.len=(int) l;}
		public boolean equals(Object o){
			if(o instanceof Request){
				Request r =(Request) o;
				return r.index==index&&begin==r.begin&&len==r.len;
			}
			return false;
		}
		@Override
		public int hashCode(){
			return index+begin+len;
		}
	}
	//TODO: make Java appropriate. 
	public static class PeerMessage{
		public static enum Type {
			CHOKE, UNCHOKE, INTERESTED, NOT_INTERESTED, HAVE, BIT_FILED, REQUEST, PIECE, CANCEL, PORT,EXTENSION
		}
		public final Type type;
		///UGHHHH. Bad. but not bad enough.
		public byte[] bitfield;
		public long piece;
		public long index;
		public long begin;
		public long length;
		public byte [] block;
		public int port;
		public int extensionID;
		public byte[] extension;
		
		public PeerMessage(Type t){type=t;}
	}
	
	private  Queue<PeerMessage> queue= new LinkedList<PeerMessage>();
	private boolean intro;
	private byte [] buffer;
	private int off;
	byte [] sizeBuf;
	private int size;//always the first byte, always in int range.
	public MessageParser(){ intro=false;size=-1;sizeBuf=new byte[4];}
	
	/***
	 * Very simple! Parses current buffer.
	 * To retrieve make a poll request.
	 * @throws IOException 
	 */
	public void readMessage(InputStream in) throws IOException{
//		if(!intro){throw new RuntimeException("Haven't called readHandShake() till completion!?!");}
		while(in.available()>0){
			if(size==-1){
				off+=in.read(sizeBuf, off, 4-off);
				if(off == 4){
					off = 0;
					size = ByteUtils.readInt(sizeBuf,0);
					buffer = new byte[size];
					
					if(size>1024*1024*10 || size<0){
						throw new IOException("INVALID PARSE EXCEPTION "+size);
					}
				}
			}else{
				try{
				off+=in.read(buffer, off, size-off);
				}catch(Exception e){
					System.out.println("ERROR: Size: "+size+" off:"+off);
					throw new IOException("They dont talk to right!");
				}
				
				if(off==size){
					if(size!=0){//zero is just keep alive.
						PeerMessage pm = from(buffer);
						if(pm!=null)
						queue.add(pm);
					}
					off=0;//reset state variables.
					size=-1;//reset state variables.
					
				}
			}
		}
	}
	
	/***
	 * Called until handShake complete
	 * Non-blocking ^.^
	 * @param in
	 * @return null if not yet complete header
	 * 		   HandShake if complete.
	 * @throws IOException
	 */
	public HandShake readHandShake(InputStream in) throws IOException{
		while (in.available() > 0) {
			if (size == -1) {
				size = in.read();
				buffer = new byte[48 + size];
				off = 0;
			} else {
				off += in.read(buffer, off, 48 + size-off);
				if (off == 48 + size) {
					byte [] version = Arrays.copyOfRange(buffer, 0, size);
					byte [] reserved = Arrays.copyOfRange(buffer, size, size + 8);
					byte [] hashInfo = Arrays.copyOfRange(buffer, size + 8,size + 8 + 20);
					byte [] peerID = Arrays.copyOfRange(buffer, size + 8 + 20,size + 8 + 20 + 20);
					HandShake hs = new HandShake(peerID,hashInfo,version,reserved);
					off =0;
					size=-1;
					intro = true;
					return hs;
				}
			}
		}
		return null;
	}
	
	
	/***
	 * Called until handShake complete
	 * Non-blocking ^.^
	 * @param in
	 * @return null if not yet complete header
	 * 		   HandShake if complete.
	 * @throws IOException
	 */
	public static HandShake readBlockingHandShake(InputStream in) throws IOException{
		boolean inComplete = false;
		HandShake hs = null;
		int size=-1;byte[] buffer = null;int off=0;
		while (!inComplete) {
			if (size == -1) {
				size = in.read();
				buffer = new byte[48 + size];
				off = 0;
				if(size>=100 && size<0){
					throw new RuntimeException("Invalid size "+size);
				}
			} else {
				off += in.read(buffer, off, 48 + size-off);
				if (off == 48 + size) {
					byte [] version = Arrays.copyOfRange(buffer, 0, size);
					byte [] reserved = Arrays.copyOfRange(buffer, size, size + 8);
					byte [] hashInfo = Arrays.copyOfRange(buffer, size + 8,size + 8 + 20);
					byte [] peerID = Arrays.copyOfRange(buffer, size + 8 + 20,size + 8 + 20 + 20);
					hs = new HandShake(peerID,hashInfo,version,reserved);
					inComplete=true;
				}
			}
		}
		return hs;
	}
	
	
	
	
	
	
	//Ok fine what you had before would have made this a bit quicker....
	private PeerMessage from(byte[] buffer2){
		PeerMessage PM;
		switch(buffer2[0]){
			case 0:
				return PM = new PeerMessage(PeerMessage.Type.CHOKE);
			case 1:
				return PM = new PeerMessage(PeerMessage.Type.UNCHOKE);
			case 2:
				return PM = new PeerMessage(PeerMessage.Type.INTERESTED);
			case 3:
				return PM = new PeerMessage(PeerMessage.Type.NOT_INTERESTED);
			case 4:
				PM = new PeerMessage(PeerMessage.Type.HAVE);
				
				PM.piece = ByteUtils.readUnsignedInt(buffer2, 1);
				return PM;
			case 5:
				PM = new PeerMessage(PeerMessage.Type.BIT_FILED);
				PM.bitfield = Arrays.copyOfRange(buffer2, 1, buffer2.length);
				return PM;
			case 6:
				PM = new PeerMessage(PeerMessage.Type.REQUEST);
				PM.index = ByteUtils.readUnsignedInt(buffer2, 1);
				PM.begin = ByteUtils.readUnsignedInt(buffer2, 5);
				PM.length = ByteUtils.readUnsignedInt(buffer2, 9);
				return PM;
			case 7:
				PM = new PeerMessage(PeerMessage.Type.PIECE);
				PM.index = ByteUtils.readUnsignedInt(buffer2, 1);
				PM.begin = ByteUtils.readUnsignedInt(buffer2, 5);
				PM.block = Arrays.copyOfRange(buffer2, 9, buffer2.length);
				return PM;
			case 8:
				PM = new PeerMessage(PeerMessage.Type.CANCEL);
				PM.index = ByteUtils.readUnsignedInt(buffer2, 1);
				PM.begin = ByteUtils.readUnsignedInt(buffer2, 5);
				PM.length = ByteUtils.readUnsignedInt(buffer2, 9);
				return PM;
			case 9:
				PM = new PeerMessage(PeerMessage.Type.PORT);
				//TODO: WE SHALT SUPPORT
				throw new RuntimeException("Not yet implemented: "+buffer2[0]);
			case 20:
				//Extension message. We now support :-)
				PM = new PeerMessage(PeerMessage.Type.EXTENSION);
				PM.extensionID = buffer2[1];
				PM.extension = Arrays.copyOfRange(buffer2, 2, buffer2.length);
				return PM;
				
			default:
					throw new RuntimeException("Message parser unknown id: "+buffer2[0]);
		}
	}

	
	public boolean hasMessage(){
		return !queue.isEmpty();
	}
	
	public PeerMessage getNext(){
		return queue.poll();
	}
	
	//I dunno change it if you want. Its just boiler
	//////|||SENDING CODE|||/////
	
	//handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
	public void sendHandShake(OutputStream os,byte [] infoHash,byte[] peerID) throws IOException{
		byte [] sptr = "BitTorrent protocol".getBytes("UTF-8");
		byte [] reserved ={0,0,0,0,0,0,0,0};//eight (8) reserved bytes. All current implementations use all zeroes
//		reserved[5] = (byte) (reserved[5]|(1<<4));//EXTENSION PROTOCOL IS NOW SUPPORTED! 0x10 -> set in 5th byte
//		if((byte)(reserved[5]&0x10) >0){
//			System.out.println("Works?");
//		}
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		bo.write(19);
		bo.write(sptr);
		bo.write(reserved);
		bo.write(infoHash);
		bo.write(peerID);
		byte [] o =bo.toByteArray();
		if(o.length!=68){
			throw new RuntimeException("Length incorrect.");
		}
		os.write(o);
	}
	
	
	public void choke(OutputStream os) throws IOException{
		byte [] o = new byte[5];
		o[ByteUtils.writeInt(1,o,0)]=0;//troll
		os.write(o);
	}
	
	public void unchoke(OutputStream os) throws IOException{
		byte [] o = new byte[5];
		o[ByteUtils.writeInt(1,o,0)]=1;//troll
		os.write(o);
	}
	
	public void interested(OutputStream os) throws IOException{
		byte [] o = new byte[5];
		o[ByteUtils.writeInt(1,o,0)]=2;//troll
		os.write(o);
	}
	
	public void not_interested(OutputStream os) throws IOException{
		byte [] o = new byte[5];
		o[ByteUtils.writeInt(1,o,0)]=3;//troll
		os.write(o);
	}
	
	//Rofl we support out but not in.? //TODO: or maybe im just tired?
	public void have(OutputStream os, long index) throws IOException{
		byte [] o = new byte[5+4];
		o[ByteUtils.writeInt(5,o,0)]=4;//troll
		ByteUtils.writeInt(index,o,5);
		os.write(o);
	}
	
	public void bitfield(OutputStream os, byte[] out) throws IOException{
		byte [] o = new byte[5+out.length];
		o[ByteUtils.writeInt(1+out.length,o,0)]=5;//troll
		ByteUtils.writeBytes(out,o,5);
		os.write(o);
	}
	
	public void request(OutputStream os, long index, long begin,long length) throws IOException{
		byte [] o = new byte[13+4];
		o[ByteUtils.writeInt(13,o,0)]=6;//troll
		int i =ByteUtils.writeInt(index,o,5);
		i=ByteUtils.writeInt(begin,o,i);
		ByteUtils.writeInt(length,o,i);
		os.write(o);
	}
	
	public void piece(OutputStream os, long index, long begin,byte [] block) throws IOException{
		byte [] o = new byte[9+4+block.length];
		o[ByteUtils.writeInt(9+block.length,o,0)]=7;//troll
		int i =ByteUtils.writeInt(index,o,5);
		i=ByteUtils.writeInt(begin,o,i);
		ByteUtils.writeBytes(block,o,i);
		os.write(o);
	}
	
	public void cancel(OutputStream os, long index, long begin,long length) throws IOException{
		byte [] o = new byte[13+4];
		o[ByteUtils.writeInt(13,o,0)]=8;//troll
		int i =ByteUtils.writeInt(index,o,5);
		i=ByteUtils.writeInt(begin,o,i);
		ByteUtils.writeInt(length,o,i);
		os.write(o);
	}
	
	public void extension(OutputStream os,byte [] pushIT) throws IOException{
		os.write(pushIT);
	}
	
	//TODO: Port.	
}
