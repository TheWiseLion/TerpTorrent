package Primitives;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import Utils.Bencoding;

public class MetaData {
	
	boolean complete;
	Piece p;
	long size;
	int pieces;
	byte [] metaData;
	Set<Integer> piecesLeft;
	boolean started =false;
	Random random =new Random();
	public MetaData(Bencoding infoDic) throws FileNotFoundException{
		complete = true;
		metaData = infoDic.toByteArray();
	}
	
	public MetaData(){
		complete = false;
		size = -1;
	}
	
	public void setSize(int bytes){
		if(!started){
			started =true;
			p = new Piece(-1,bytes);
			complete = false;
			size = bytes;
			pieces= (int) Math.ceil(size/(double)(1024*16.0));
			metaData = new byte[(int) size];
			piecesLeft = new HashSet<Integer>();
			for(int i=0;i<pieces;i++){
				piecesLeft.add(i);
			}
		}
	}
	
	
	public byte[] getPiece(int i) throws IOException{
		if(complete){
			int len = 1024*16;
			if(len/(1024*16) == pieces-1){
				len = (int) (size%(1024*16));
				if(len==0){ len=1024*16;}
			}
			byte r[] = new byte[len];
			System.arraycopy(metaData, i*1024*16, r, 0, len);
			return r;
		}
		return null;
	}
	
	public void add(int piece, byte[] data){
		if(!complete){
			p.addData(1024*16*piece, data);
			piecesLeft.remove(piece);
		}
	}
	
	/**
	 * Ideally one not already given out. but we ain't picky.
	 * @return
	 */
	public int getOpenPiece(){
		if(piecesLeft.size()>0){
			int i = random.nextInt(piecesLeft.size());
			return piecesLeft.toArray(new Integer[0])[i];//Hawt. 
		}else{
			return -1;
		}
	}
	
	public boolean isComplete(){
		return complete;
	}
	
	public boolean shaMatch(byte [] infoHash) throws NoSuchAlgorithmException{
		if(complete){
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			return Arrays.equals(infoHash, md.digest(metaData));
		}
		return false;
	}
	
	public void clear(){
		//fuuuuuuuuuk i hope this doesnt happen.
		//Could be byzentine nodes tho...  :-/
		complete =false;
		started = false;
	}
	
}
