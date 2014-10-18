package Main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import NetBase.ManagedConnection;
import NetBase.MessageParser;
import NetBase.MessageParser.HandShake;
import NetBase.MessageParser.PeerMessage;
import Utils.Bencoding;
import Utils.HttpResponse;
import Utils.HttpResponse.HeaderType;

public class UnitTests {

	
	private static void testBencoding(){
		String s="4:spam";
		Bencoding b0=new Bencoding(s.getBytes(Charset.forName("UTF-8")));
		System.out.println("	Test0: "+"spam".equals(b0.getString()));
		
		String i="i18296718e";
		Bencoding b1=new Bencoding(i.getBytes(Charset.forName("UTF-8")));
		System.out.println("	Test1: "+ (18296718==b1.integer));
		
		String l="l4:spami42eli52eee";//List in list
		Bencoding b2=new Bencoding(l.getBytes(Charset.forName("UTF-8")));
		System.out.println("	Test2: "+("spam".equals(b2.list.get(0).getString()) && 42== b2.list.get(1).integer&&52 == b2.list.get(2).list.get(0).integer));
//		
		String d="d3:bar4:spam3:fooi42e5:listsl5:12345d3:foo3:fooeee";
		Bencoding b3=new Bencoding(d.getBytes(Charset.forName("UTF-8")));
		System.out.println("	Test3: "+("spam".equals(b3.dictionary.get("bar").getString()) && 42==b3.dictionary.get("foo").integer));
	}
	
	
	//Simple test to show that content-length not being complete will not cause crash, and that it will terminate when 
	//more content then content length suggests. 
	private static void parseHTTPResponse() throws IOException{
		String sampleHTTPResponse = "HTTP/1.0 200 OK\r\nContent-Length: 6\r\nContent-Type: text/plain\r\nPragma: no-cache\r\n\r\nTEAS";
		byte [] input = sampleHTTPResponse.getBytes("UTF-8");
		InputStream in = new ByteArrayInputStream(input);
		HttpResponse phr = new HttpResponse(in);
		System.out.println("	Test ContentSize: "+(phr.headerMap.get(HeaderType.ContentLength).equals("6")));
		System.out.println("	Test ContentActualSize: "+(phr.body.length==4));
		System.out.println("	Test Out: "+new String(phr.body,0,phr.body.length,"UTF-8").equals("TEAS"));
	}
	
	
	public final static byte[] testGen(int len){
		byte [] b =new byte[len];
		for(int i=0;i<len;i++){
			b[i]=(byte) Math.random();
		}
		return b;
	}
	
	/**
	 * How many off by one errors are there going to be?!?
	 * @param args
	 * @throws IOException 
	 */
	private static void messageParser() throws IOException{
		//Ok, lets use the potentially broken thing ot test the broken thing
		//right?
		
		MessageParser MP = new MessageParser();
		ByteArrayOutputStream os=new  ByteArrayOutputStream();
		ByteArrayInputStream bis;
		byte [] info = testGen(20);
		byte [] id = testGen(20);
		byte [] bitField = testGen(80);
		MP.sendHandShake(os,info,id);
		bis = new ByteArrayInputStream(os.toByteArray());
		HandShake hs = MP.readHandShake(bis);
		System.out.println("	Test 1: "+(Arrays.equals(info,hs.hashInfo)&&Arrays.equals(id,hs.peerID)));
		//MP.bitfield(os, new byte[50]);
		os.reset();
		MP.choke(os);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		System.out.println("	Test 2: "+(MP.getNext().type==PeerMessage.Type.CHOKE));
		
		
		os.reset();
		MP.unchoke(os);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		System.out.println("	Test 3: "+(MP.getNext().type==PeerMessage.Type.UNCHOKE));
		
		os.reset();
		MP.interested(os);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		System.out.println("	Test 4: "+(MP.getNext().type==PeerMessage.Type.INTERESTED));
		
		os.reset();
		MP.not_interested(os);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		System.out.println("	Test 5: "+(MP.getNext().type==PeerMessage.Type.NOT_INTERESTED));
		
		os.reset();
		MP.bitfield(os,bitField);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		System.out.println("	Test 6: "+(Arrays.equals(bitField, MP.getNext().bitfield)));
		
		os.reset();
		MP.have(os,10241139);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		System.out.println("	Test 7: "+(MP.getNext().piece==10241139));
		
		os.reset();
		MP.request(os, 10199,10211,12311);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		PeerMessage p = MP.getNext();
		System.out.println("	Test 8: "+(p.begin==10211&&p.index==10199&&p.length==12311&&p.type==PeerMessage.Type.REQUEST));
		
		byte [] block = testGen(10234);
		os.reset();
		MP.piece(os, 0, 300, block);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		p = MP.getNext();
		System.out.println("	Test 9: "+(Arrays.equals(block, p.block)&&p.index==0&&p.begin==300));
		
		os.reset();
		MP.cancel(os, 10199,10211,12311);
		bis = new ByteArrayInputStream(os.toByteArray());
		MP.readMessage(bis);
		p = MP.getNext();
		System.out.println("	Test 10: "+(p.begin==10211&&p.index==10199&&p.length==12311&&p.type==PeerMessage.Type.CANCEL));
		
		
	}
	
	
	
	
	
	
	
	
	public static void main(String [] args) throws IOException, NoSuchAlgorithmException{
		System.out.println("Dry-run tests (true = pass)");
		System.out.println("Bencoding: ");
		testBencoding();
		System.out.println("HTTP Response Parsing: ");
		parseHTTPResponse();
		System.out.println("Torrent message parsing:");
		messageParser();
//		System.out.println("Managed Connection:");
//		ManagedConnection.testManagedConnection();
		System.out.println("Other Tests exists @PieceManager");
		
	}
}
