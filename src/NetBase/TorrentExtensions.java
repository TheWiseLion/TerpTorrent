package NetBase;

import java.util.HashMap;

import Utils.Bencoding;
import Utils.Bencoding.Type;
import Utils.ByteUtils;

/**
 * Message constructors and parsers for torrent extensions
 * -Magnet links
 * - DHT
 * @author wiselion
 *
 */
public class TorrentExtensions {
	//Construct Extension:
	//TODO: efficiency?
	public final static byte[] constructExcetention(byte id, byte[] eMessage){
		byte [] b = new byte[eMessage.length+4+1+1];
		ByteUtils.writeInt(b.length-4, b, 0);
		b[4]=20;
		b[5]=id;
		System.arraycopy(eMessage, 0, b, 6, eMessage.length);
		return b;
	}
	
	/////////////////// "Extension for Peers to Send Metadata Files" /////////////////////
	public final static byte[] getMetaDataPiece(byte id, int piece){
		Bencoding b = new Bencoding(); 
		b.type = Type.Dictionary;
		b.dictionary = new HashMap<String,Bencoding>();
		b.dictionary.put("msg_type", new Bencoding(0));
		b.dictionary.put("piece", new Bencoding(piece));
		return constructExcetention(id,b.toByteArray());
	}
	
	public final static byte[] pushMetaDataPiece(byte id, int piece,byte [] block){
		Bencoding b = new Bencoding(); 
		b.type = Type.Dictionary;
		b.dictionary = new HashMap<String,Bencoding>();
		b.dictionary.put("msg_type", new Bencoding(1));
		b.dictionary.put("piece", new Bencoding(piece));
		b.dictionary.put("total_size", new Bencoding(block.length));
		byte [] dic =b.toByteArray();
		byte [] r = new byte[dic.length+block.length];
		System.arraycopy(dic, 0, r, 0,dic.length);
		System.arraycopy(block, 0, r, dic.length, block.length);
		return constructExcetention(id,b.toByteArray());//quite wasteful. but im lazy :-)
	}
	
	public final static byte[] rejectMetaDataPiece(byte id,int piece){
		Bencoding b = new Bencoding(); 
		b.type = Type.Dictionary;
		b.dictionary = new HashMap<String,Bencoding>();
		b.dictionary.put("msg_type", new Bencoding(2));
		b.dictionary.put("piece", new Bencoding(piece));
		return constructExcetention(id,b.toByteArray());
	}
	//\\\\\\\\\\\\\\\\ "Extension for Peers to Send Metadata Files" \\\\\\\\\\\\\\\\\\\\
	
	
	
}
