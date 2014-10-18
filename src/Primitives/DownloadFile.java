package Primitives;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import TorrentData.PieceManager;

/**
 * Pieces may overlap file boundaries.
 * Well. Crap. Use byte offset to calculate piece info.
 * Either this file is a pointer to an actual file.
 * OR its a partial file.
 * 
 * 
 * Partial File Format:
 * <piece index(4)><piece data(pieceLength)>
 * Why the extra 4 bytes?
 * What if we only partially download, thats why.
 * 
 */
public class DownloadFile {
	
	private long length, byteOffSet;//TODO: offset zero based?
	private int leftPiece, rightPiece, pieceLength;
	private RandomAccessFile raf;
	private boolean isPartial;
	private Map<Integer,Long> pieceIndexToOffSet;
	private int numPieces;
	private String topFolder;
	private String pathAndName;
	private File f;
	public DownloadFile(String topFolder,String pathAndName,long length,long byteOff){//shit parsed from torrent file
		this.length=length;
		this.byteOffSet=byteOff;
		this.topFolder = topFolder;
		this.pathAndName = pathAndName;
		pieceIndexToOffSet = new HashMap<Integer,Long>();
	}
	
	public void initialize(int pieceLength,BitMap bm) throws IOException{
		this.pieceLength = pieceLength;
		this.leftPiece = (int) (byteOffSet/pieceLength);
		this.rightPiece = (int) Math.ceil((double)(byteOffSet+length)/pieceLength)-1;//len 0?
		numPieces = rightPiece - leftPiece + 1;
		f = new File(topFolder+"\\"+pathAndName);
		if(f.isFile()){
			//file exists. 
			//Act as if its all there.
			//(we check more in depth later.)
			raf = new RandomAccessFile(f,"rw");
			int pStart = (int)byteOffSet/pieceLength;
			int pEnd =(int)Math.ceil((double)(byteOffSet + length)/pieceLength);
			for(int s=pStart;s<pEnd;s++){
				long fp = getIdeaFilePoint(s);
				pieceIndexToOffSet.put(s, fp);
				bm.addPieceComplete(s);
			}
			isPartial = false;
			return;
		}
		isPartial = true;
		//ok file isnt here. is the .partial there?
		f = new File(topFolder+"\\"+pathAndName+".partial");
		if(f.isFile()){
			raf = new RandomAccessFile(f,"rw");
			//read through
			raf.seek(0);
			while(raf.getFilePointer()<raf.length()){
				int pIndex = raf.readInt();
				bm.addPieceComplete(pIndex);
				pieceIndexToOffSet.put(pIndex,raf.getFilePointer());
				int i = getSizeFrom(pIndex);
				raf.skipBytes(i);
			}
			
			return;
		}
	
		//ok neither exist. lets make this now.
		if(f.getParent()!=null){
			File fp = new File(f.getParent());
			fp.mkdirs();
		}
		raf = new RandomAccessFile(f,"rw");
	}
	
	public long getOffSet(){
		return byteOffSet;
	}
	
	public long getLength(){
		return length;
	}
	
	/**
	 * Writes piece to array.
	 * Amount of bytes written is returned.
	 * the byte array is expected to be large enough
	 * @param index - piece index to be gotten
	 * @param off - off set int bye array
	 * @param b - byte array retults be written to
	 * @return amount of bytes written
	 * @author wiselion
	 * @throws IOException 
	 */
	public int getPiece(int index,int off,byte [] b) throws IOException{
		if(leftPiece<=index && rightPiece>=index&&pieceIndexToOffSet.containsKey(index)){
			//***We can assume file contains it.***
			//***So either parly contained or fully.***
			
			//get piece section size
			long fileOff = pieceIndexToOffSet.get(index);
			long pStart = (long)(index*(long)pieceLength);//byte at which piece starts
			//get the bytes that overlap.
			long fStart= pStart-byteOffSet; //file start - pieceStart
			long fEnd = pStart + pieceLength-byteOffSet;
			//Does piece go past end of file?
			if(fEnd>length){
				fEnd=length;
			}
			//Does piece begin in file?
			if(fStart<0){//doesnt start in here. so copy what does.
				fStart=0;
			}
			
			raf.seek(fileOff);
			return raf.read(b, off,(int)(fEnd-fStart));//hot stuff
		}
		return 0;
	}
	
	
	/**
	 * Writes as much of the piece that exists in this file
	 * returns error if file is complete
	 * @param piece to be written 
	 * @throws IOException 
	 */
	public void writePiece(Piece p) throws IOException{
		//if(!isPartial){throw new RuntimeException("Invalid use. I'm complete");}
		if(pieceIndexToOffSet.containsKey((int)p.pieceIndex)){throw new RuntimeException("Invalid piece is already contained.");}
		if(leftPiece<=p.pieceIndex && rightPiece>=p.pieceIndex){
			long pStart = (long)(p.pieceIndex*(long)pieceLength);//byte at which piece starts
			long fStart= pStart-byteOffSet; //file start - pieceStart
			long fEnd = pStart + pieceLength-byteOffSet;
			int offX=0;
			if(fEnd>length){
				fEnd = length;
			}
			if(fStart<0){
				offX=(int) Math.abs(fStart);
				fStart =0;
			}
			
			raf.seek(raf.length());
			raf.writeInt((int)p.pieceIndex);
			long pt = raf.getFilePointer();
			raf.write(p.finalPiece, offX, (int)(fEnd-fStart));
			//check if complete. If it is change status.
			pieceIndexToOffSet.put((int)p.pieceIndex, pt);
			if(numPieces == pieceIndexToOffSet.size()){
				//Complete. 
				//Do our close out.
				doCloseOut();
			}
		}
	}
	

	
	public void close() throws IOException{
		raf.close();
	}
	
	public boolean isComplete(){
		return !isPartial;
	}
	
	public static byte[] pieceFromFile(DownloadFile files[],int pieceIndex,int pieceLength) throws IOException{
		byte [] piece = new byte[pieceLength];
		int off = 0;
		for(DownloadFile f:files){
			off+= f.getPiece(pieceIndex,off,piece);
		}
		if(off != pieceLength){
			return null;
		}
		return piece;
	}
	
	
	
/////////////////// Private Methods aka junk. //////////////////////
	private byte[] getPiece(int index) throws IOException{
		// ***We can assume file contains it.***
		// ***So either parly contained or fully.***
		// get piece section size
		long fileOff = pieceIndexToOffSet.get(index);
		long pStart = (long) (index * (long) pieceLength);// byte at which piece
															// starts
		// get the bytes that overlap.
		long fStart= pStart-byteOffSet; //file start - pieceStart
		long fEnd = pStart + pieceLength - byteOffSet;
		// Does piece go past end of file?
		if (fEnd > length) {
			fEnd = length;
		}
		// Does piece begin in file?
		if (fStart < 0) {// doesnt start in here. so copy what does.
			fStart = 0;
		}

		raf.seek(fileOff);
		byte b [] = new byte[(int) (fEnd - fStart)];
		raf.read(b);
		return b;// hot stuff
	}
	
	private void doCloseOut() throws IOException{
		isPartial=false;
		//check if some how the file exists. If does DELETE IT!
		f = new File(topFolder+"\\"+pathAndName);
		if(f.isFile()){
			f.delete();
		}
		if(f.getParent()!=null){//Make dir's if needed
			File dir = new File(f.getParent());
			dir.mkdirs();
		}
		
		RandomAccessFile finalFile = new RandomAccessFile(f, "rw");//Open file
		long l = ((byteOffSet + length)%pieceLength);
		if(l==0){
			l=pieceLength;
		}
		long pStart = byteOffSet/pieceLength;
		long pEnd =(long)Math.ceil((double)(byteOffSet + length)/pieceLength);
		finalFile.seek(0);
		for(long s=pStart;s<pEnd;s++){
			//now we start writing ^.^
			byte [] b =getPiece((int)s);
			finalFile.write(b);
		}
		raf.close();
		raf = finalFile;
	}

	private long getIdeaFilePoint(int pieceNum){
		long pStart = (long)(pieceNum*(long)pieceLength);//byte at which piece starts
		long fStart= byteOffSet-pStart; //file start - pieceStart
		long fEnd = pStart + pieceLength-byteOffSet;
		int offX=0;
		if(fEnd>length){
			fEnd = length;
		}
		if(fStart<0){
			offX=(int) Math.abs(fStart);
			fStart =0;
		}
		
		
		return offX;
	}
	
	private int getSizeFrom(int pieceNum){
		long pStart = (long)(pieceNum*(long)pieceLength);//byte at which piece starts
		long fStart= pStart-byteOffSet; //file start - pieceStart
		long fEnd = pStart + pieceLength-byteOffSet;
		if(fEnd>length){
			fEnd = length;
		}
		if(fStart<0){
			fStart =0;
		}
		return (int)(fEnd-fStart);
	}
	
	
	
	private static final Random r = new Random(); 
	public static byte[] generateSessionKey(int length) throws UnsupportedEncodingException{
		String alphabet =new String("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"); 
		int n = alphabet.length(); 
		String result = new String(); 
		for (int i=0; i<length; i++) 
		    result = result + alphabet.charAt(r.nextInt(n));
	
		return result.getBytes("UTF-8");
	}

	public void delete() throws IOException {
		raf.close();
		if(f!=null){
			f.delete();
		}
	}
	
	
	
	
}
