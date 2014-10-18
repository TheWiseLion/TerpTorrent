package TorrentData;

import Primitives.MetaData;

/**
 * Mostly created due to the need for there to be
 * a data type that was minimal enough to support 
 * getting meta data through meta data extension.
 *
 * @author wiselion
 *
 */
public class MetaTorrent {
	public final byte[] hashInfo;
	public final byte[] peerID;
	public final MetaData meta;
	protected String name;
	protected String status ="";
	public MetaTorrent(byte[] hashInfo, byte[] peerID,MetaData md) {
		this.hashInfo = hashInfo;
		this.peerID = peerID;
		this.meta = md;
	}
	
	public String getStatus(){
		return status;
	}
	
}
