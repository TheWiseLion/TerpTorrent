package Primitives;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/***
 * Primitive data structure used to determine:
 * 1. Optimal pieces to get (rarity)
 * 2. A sharable data structure that is immutable.
 * MAX PEERS: 32k 
 */
public class BitMap {
	byte [] ourBitMap;
	public class Rarity implements Comparable<Rarity>{
		private short value;
		public final int index;
		public Rarity(int index){this.index=index;}
		@Override
		public int compareTo(Rarity o) {
			return o.value-value;
		}
		public short getCount(){return value;}
	}
	Map<Integer,Rarity> indexToRarity;
	List<Rarity> rarity;
	short [] rarityMap;
	int pieceLength;
	long totalData;
	int numPieces;
	List<BitMap> peerMaps;
	long piecesComplete=0;
	public BitMap(long totalData, int pieceLength){
		this.totalData = totalData;
		numPieces = (int) Math.ceil((double)totalData/pieceLength);//TODO off by 1?
		ourBitMap = new byte[(int)Math.ceil(numPieces/8.0)];
		peerMaps = new ArrayList<BitMap>();
		rarity = new ArrayList<Rarity>();
		indexToRarity= new HashMap<Integer,Rarity>();
		rarityMap = new short[numPieces];
		for(int i=0;i<numPieces;i++){
			Rarity r = new Rarity(i);
			indexToRarity.put(r.index,r);
			rarity.add(r);
		}
		this.pieceLength = pieceLength;
		
	}
	
	
	
	public void addPieceComplete(long i){
		if(!hasPiece((int)i)){
			int index = (int) i;
			byte  b= ourBitMap[index/8];
			b = (byte) (b | (1 << (7-index%8)));
			ourBitMap[index/8] = b;
			piecesComplete++;
		}
	}
	
	public boolean hasPiece(int index){
		boolean b = (ourBitMap[index/8] & (1 << (7-index%8))) != 0;
		return b;
	}
	
	/***
	 * O(peers*pieces)
	 * Grows linearly with number of peers
	 * This absolutely bonkers.
	 * TODO: i dunno might beable to think of something better.
	 */
	public void recomputeRarity(){
		for(int i=0;i<rarityMap.length;i++){
			rarityMap[i]=0;
		}
		for(int i=0;i<numPieces;i++){
			for(BitMap b: peerMaps){
				rarityMap[i] +=b.hasPiece(i)?1:0;
			}
			indexToRarity.get(i).value=rarityMap[i];
		}
		Collections.sort(rarity);
	}
	
	public void setBitMap(byte [] bitmap){
		if(bitmap.length != this.ourBitMap.length){
			throw new RuntimeException("invalid bitmap length");
		}
		for(int i=0;i<bitmap.length;i++){
			this.ourBitMap[i]=bitmap[i];
		}
		piecesComplete=0;
		for(int i=0;i<numPieces;i++){
			if(hasPiece(i)){
				piecesComplete++;
			}else{
				System.out.println("Missing: "+i);
			}
		}
	}
	
	public byte[] getMapCopy(){
		return ourBitMap.clone();
	}
	
	public int getPieceLength(int index){
		int pSize = pieceLength;
		if(index == numPieces-1){
			pSize = (int) (totalData%pieceLength);//final piece!
			if(pSize==0){
				pSize=pieceLength;
			}
		}
		return pSize;
	}
	
	public Piece createPiece(int index){
		int pSize =getPieceLength(index);
		if(index>=numPieces){
			throw new RuntimeException("Invalid index: "+index+" only "+numPieces+" pieces");
		}
		if(index == numPieces-1){
			
		}
		
		Piece p =new Piece(index,pSize);
		return p;
	}
	
	public void addPeerMap(BitMap bm){
		if(!peerMaps.contains(bm)){
			peerMaps.add(bm);
		}
	}
	

	public boolean removePeerMap(BitMap bm){
		return peerMaps.remove(bm);
	}
	
	public boolean isComplete(){
		return piecesComplete == numPieces; 
	}
	
	public long getCompletedPieces(){
		return piecesComplete;
	}
	
	public int getNumberOfPieces(){
		return numPieces;
	}
	
	public long getTotalSize(){
		return totalData;
	}
	
	public int getLength(){
		return ourBitMap.length;
	}
	
	public List<Rarity> getRarity(){
		return rarity;
	}
	
	
}
