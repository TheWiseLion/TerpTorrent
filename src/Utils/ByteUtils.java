package Utils;


public class ByteUtils {
	
	public static long readUnsignedInt(char [] data,int offset){
		return (((long)data[offset++]&0xFF) << 24) | 
				(((long)data[offset++]&0xFF) << 16) |
				(((long)data[offset++]&0xFF) << 8) | 
				((long)data[offset++]&0xFF);
	}
	
	public static int readInt(char[] data, int offset){
		return ((data[offset++]&0xFF) << 24) | 
				((data[offset++]&0xFF) << 16) |
				((data[offset++]&0xFF) << 8) | 
				(data[offset++]&0xFF);
	}

	public static int readIntH(byte[] data, int offset){
		return ((data[offset]&0xFF) << 24) | 
				((data[offset+1]&0xFF) << 16) |
				((data[offset+2]&0xFF) << 8) | 
				(data[offset+3]&0xFF);
	}
	
	public static int readIntL(byte[] data, int offset){
		return ((data[offset+3]&0xFF) << 24) | 
				((data[offset+2]&0xFF) << 16) |
				((data[offset+1]&0xFF) << 8) | 
				(data[offset]&0xFF);
	}
	
	public static long readUnsignedInt(byte [] data,int offset){
		return (((long)data[offset++]&0xFF) << 24) | 
				(((long)data[offset++]&0xFF) << 16) |
				(((long)data[offset++]&0xFF) << 8) | 
				((long)data[offset++]&0xFF);
	}
	
	public static int readInt(byte[] data, int offset){
		return ((data[offset++]&0xFF) << 24) | 
				((data[offset++]&0xFF) << 16) |
				((data[offset++]&0xFF) << 8) | 
				(data[offset++]&0xFF);
	}
	
	
	public static int writeInt(int i, byte[] data, int offset){
		data[offset++] = (byte)((i >> 24)&0xFF); 
		data[offset++] = (byte)((i >> 16)&0xFF);
		data[offset++] = (byte)((i >> 8)&0xFF);
		data[offset++] = (byte)(i & 0xFF);
		return offset;
	}
	public static int writeInt(long i, byte[] data, int offset){
		data[offset++] = (byte)((i >> 24)&0xFF); 
		data[offset++] = (byte)((i >> 16)&0xFF);
		data[offset++] = (byte)((i >> 8)&0xFF);
		data[offset++] = (byte)(i & 0xFF);
		return offset;
	}
	
	public static int writeBytes(byte [] in, byte[] data, int offset){
		for(int i =0;i<in.length;i++){
			data[offset+i]=in[i];
		}
		return offset+in.length;
	}
	
	public static short readShort(byte [] data,int offset){
		return (short) ((short) ((data[offset++]&0xFF) << 8) | 
				(data[offset++]&0xFF));
	}
}
