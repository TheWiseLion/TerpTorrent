package Primitives;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import NetBase.MessageParser.Request;

/***
 * Probably more to this...
 */
public class Piece implements Comparable<Piece>{
	public static class SubPiece implements Comparable<SubPiece>{
		int begin;
		byte [] data;
		public SubPiece(long begin,byte[]data){this.begin=(int) begin;this.data=data;}
		@Override
		public int compareTo(SubPiece sp) {
			// TODO Auto-generated method stub
			return begin-sp.begin;
		}
		@Override
		public boolean equals(Object o){
			if(o instanceof SubPiece){
				SubPiece sp = (SubPiece)o;
				return sp.begin==begin&&data.length==sp.data.length;
			}
			return false;
		}
		
	}
	boolean complete;
	//Strictly, the specification allows 2^15 (32KB) requests.
	//The reality is near all clients will now use 2^14 (16KB) requests.
	public final static int idealSize = 16 *1024;
	public final long pieceIndex;
	final byte [] finalPiece;
	Set<SubPiece> data;
	
	public Piece(long index, int size){//allows for last piece to not be pieceLength.
		finalPiece = new byte[size];
		this.pieceIndex = index;
		data = new TreeSet<SubPiece>();
		complete=false;
	}
	
	public Piece(long index,byte [] d){
		finalPiece = d;
		this.pieceIndex=index;
		complete=true;
	}
	/**
	 * Used by connection adds block to piece
	 * returns true if complete.
	 * @param begin
	 * @param block
	 */
	public boolean addData(long begin,byte [] block){
		if(complete){ throw new RuntimeException("Piece complete yet still adding more");}
		data.add(new SubPiece(begin,block));
		Iterator<SubPiece> itor = data.iterator();
		int last=0;//final position
		while(itor.hasNext()){
			SubPiece s=itor.next();
			if(last==s.begin){
				if(s.begin+s.data.length==finalPiece.length){
					completePiece();
					return true;
				}
			}else{
				return false;
			}
			last = s.begin+s.data.length;
		}
		
		return false;
	}
	
	/**
	 * Returns null if complete.
	 * Other wise returns a request of next
	 * block in the piece. "Ideal" is 
	 * 16KB
	 * @return
	 */
	public Request getNextBlock(){
		if(complete){return null;}
		Iterator<SubPiece> itor = data.iterator();
		SubPiece last=null;
		int max = idealSize;
		if(max > finalPiece.length){
			max = finalPiece.length;
		}
		//TODO: reduces i think?
		while(itor.hasNext()){
			if(last==null){
				last=itor.next();
			}else{
				SubPiece curr =itor.next();
				if(curr.begin>last.begin+last.data.length){//plzz dont be off by one kay thx
					//Our next piece falls between two pieces
					int m=curr.begin-(last.begin+last.data.length);//delta aka between blocks
					m=m>max?m:max;
					return new Request(pieceIndex,last.begin+last.data.length,m);
				}
				last = curr;
			}
		}
		
		
		if(last==null){//First request for this piece ^.^
			return new Request(pieceIndex,0,max);
		}else{//At the end? Maybe?
			int m = finalPiece.length-(last.begin+last.data.length)>max?max:finalPiece.length-last.begin+last.data.length;
			if(m!=0){//if not complete.
				return new Request(pieceIndex,last.begin+last.data.length,max);
			}
		}
		return null;//its actually complete.
	}
	
	public List<Request> getAllBlocksLeft(){
		//count blocks.
		if(complete){return null;}
		List<Request> list =new ArrayList<Request>();
		SubPiece last=null;
		//TODO: simplify? bit incomprehensible 
		int i = 0;
		Iterator<SubPiece> itor = data.iterator();
		while (itor.hasNext()) {
			SubPiece sp = itor.next();
			if (sp.begin != i) {
				// we missing stuff add
				int bytes = sp.begin-i;
				for (int z = 0; z < bytes; z += idealSize) {
					if (z + idealSize > bytes) {
						list.add(new Request(pieceIndex, i + z, bytes - z));
					} else {
						list.add(new Request(pieceIndex, i + z, idealSize));
					}
				}
			}
			last = sp;
			i += sp.data.length;
		}
		// now we have to go from last
		// to end.
		if(last==null){
			last = new SubPiece(0,new byte[0]);
		}
		for(i=last.data.length+last.begin;i<finalPiece.length;i+=idealSize){
			if (i + idealSize > finalPiece.length) {
				list.add(new Request(pieceIndex, i, finalPiece.length-i));
			} else {
				list.add(new Request(pieceIndex, i, idealSize));
			}
		}
			
		return list;
	}


	//Copy into final array
	private void completePiece(){
		Iterator<SubPiece> itor = data.iterator();
		int last=0;//final position
		while(itor.hasNext()){
			SubPiece s=itor.next();
			if(last==s.begin){
				System.arraycopy(s.data, 0, finalPiece, s.begin, s.data.length);
			}else{
				throw new RuntimeException("Holy shit this is wrong.");
			}
			last+=s.data.length;
		}
		// :3
		data.clear();
		data = null;
		complete = true;
	}

	@Override
	public int compareTo(Piece arg0) {
		return (int) (this.pieceIndex-arg0.pieceIndex);
	}
	
	public boolean isComplete(){
		return complete;
	}
	
	public byte[] getCompleted(){
		if(!complete){throw new RuntimeException("Invalid!!!");}
		return finalPiece;
	}
	
	public byte[] getFromComplete(int offx, int len){
		if(!complete){throw new RuntimeException("You asked for completed piece operation yet not complete.");}
		byte [] b =new byte[len];
		System.arraycopy(finalPiece, offx, b, 0, len);
		return b;
	}

	
	 // Test:	
	 public static void main(String [] args){
		//test the get blocks simple loop should confirm this thing as stable:
		Piece p =new Piece(0,128*1024);
		Request first = p.getNextBlock();
		boolean b;
		for(int i=0;i<8;i++){
			b = p.addData(first.begin, new byte[first.len]);
			first = p.getNextBlock();
			if(b)
			System.out.println("true on i="+i);
		}
		
		//test our get all pieces function.
		p =new Piece(0,128*1024);
		p.addData(64*1024,new byte[1024]);
		List<Request> rList = p.getAllBlocksLeft();
		for(Request r: rList){
			System.out.println("Block Size "+r.len);
			p.addData(r.begin,new byte[r.len]);
		}
		//wahoo
		System.out.println("	Test 2: "+p.isComplete());
		
		
		p =new Piece(0,128*1024);
		//p.addData(64*1024,new byte[1024]);
		rList = p.getAllBlocksLeft();
		for(Request r: rList){
			System.out.println("Block Size "+r.len);
			p.addData(r.begin,new byte[r.len]);
		}
		//wahoo
		System.out.println("	Test 3: "+p.isComplete());
		
	}
	 
	
}

