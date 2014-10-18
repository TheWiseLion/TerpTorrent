package Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * From Wikipedia:
 * "The response message consists of the following:
 *	-A Status-Line (for example HTTP/1.1 200 OK, which indicates that the client's request succeeded)
 *	-Response Headers, such as Content-Type: text/html
 *	-An empty line
 *	-An optional message body"
 *
 *
 * By all means not a complete definition of all possible responses.
 * @author wiselion
 *
 */
public class HttpResponse {
	public static enum HeaderType{HTTP,Date,Server,LastModified,Etag,ContentType,ContentLength,Connection,Pragma,TransferEncoding,UKNOWN};
	//private final String ResponseHeaders[]={"HTTP","Date:","Server:","Last-Modified:","ETag:","Content-Type:","Content-Length:","Connection:","Pragma:","Set-Cookie:"};
	
	public final Map<HeaderType,String> headerMap;
	public int status = -1;
	public String version;
	public int contentSize = -1;
	public byte[] body;
	public final int HEADER_LIMITS = 10*1024;//TODO: no more then 10k in headers?
	public boolean transferEncoding;
	/**
	 * Will read till steam closes or content length reached.
	 * So dont go and give me an endless stream.
	 * Throws error if tag doesnt match. 
	 * @throws IOException 
	 */
	public HttpResponse(InputStream in) throws IOException{//Input stream. Presumably Tcp socket.
		String patternStr = "[0-9]+";
		transferEncoding = false;
	    Pattern pattern = Pattern.compile(patternStr);
		headerMap = new HashMap<HeaderType,String>();
		boolean emptyFound = false;
		byte [] dataIn;
		while(!emptyFound){
			dataIn =readRawHeaderLine(in);
			String p = bytesToString(dataIn);
			//parsing time.
			if(p==null){
				throw new RuntimeException("HTTP Parse Exception. Bugs. The bunny.");
			}
			
			if(p.contentEquals("\r\n")){
				emptyFound = true;
			}else if(p.startsWith("HTTP")){
				//parse out status and verion.
				int i = p.indexOf(" ");
				version = p.subSequence(5, i).toString();
				//read to next non digit.
				Matcher matcher = pattern.matcher(p.subSequence(i+1, p.length()));
				if(matcher.find()){
					status = new Integer(p.substring(i+1+matcher.start(),i+1+matcher.end()));
				}
				
				headerMap.put(HeaderType.ContentLength,p.substring(4).replaceAll(("[\\n\\r]"), ""));
			}else if(p.startsWith("Date:")){
				headerMap.put(HeaderType.Date,p.substring(5));
			}else if(p.startsWith("Sever:")){
				headerMap.put(HeaderType.Server,p.substring(6));
			}else if(p.startsWith("Last-Modified:")){
				headerMap.put(HeaderType.LastModified,p.substring(14));
			}else if(p.startsWith("ETag:")){
				headerMap.put(HeaderType.Etag,p.substring(5));
			}else if(p.startsWith("Content-Type:")){
				headerMap.put(HeaderType.ContentType,p.substring(13));
			}else if(p.startsWith("Content-Length:")){
				headerMap.put(HeaderType.ContentLength,p.substring(15).replaceAll(("[\\s\\n\\r]"), ""));
				contentSize =new Integer(p.substring(15).replaceAll(("[\\s\\n\\r]"), ""));
			}else if(p.startsWith("Connection:")){
				headerMap.put(HeaderType.Connection,p.substring(11));
			}else if(p.startsWith("Pragma:")){
				headerMap.put(HeaderType.Pragma,p.substring(7));
			}else if(p.startsWith("Transfer-Encoding:")){
				transferEncoding=true;
				headerMap.put(HeaderType.TransferEncoding,p.substring(18).trim());
			}else{
				System.out.println("Unknown Header: \""+p.substring(0, p.length()-2)+"\"");
				String o = p;
				if(headerMap.containsKey(HeaderType.UKNOWN)){
					o = headerMap.get(HeaderType.UKNOWN)+p;
				}
				headerMap.put(HeaderType.UKNOWN,o);
			}
		}
		
		if(contentSize ==-1&&!transferEncoding){
			System.out.println("Warning: Reading till connection closes");
			contentSize = Integer.MAX_VALUE;
			body = readBytes(in,contentSize);
		}else if(transferEncoding){
			if("chunked".equals(headerMap.get(HeaderType.TransferEncoding))){
				body = readChunked(in);
			}else{
				throw new RuntimeException("Invalid transfer encoding: \""+headerMap.get(HeaderType.TransferEncoding)+"\"");
			}
		}else{
			body = readBytes(in,contentSize);
		}
		
		
		
	}
	
	/*
	 * The Status-Line and headers must all end with <CR><LF> (a carriage return followed by a line feed).
	 * The empty line must consist of only <CR><LF> and no other whitespace.
	 */
	
	
	
	//HELPER FUNCTIONS:
	private static String bytesToString(byte[] b){
		try {
			return new String(b,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			throw new RuntimeException("Parse error");
		}
	}
	
	 public static byte[] readRawHeaderLine(InputStream inputStream) throws IOException {
	        ByteArrayOutputStream buf = new ByteArrayOutputStream();
	        int ch;
	        int lastCh=0;
	        try{
		        while ((ch = inputStream.read()) >= 0) {
		            buf.write(ch);
		            if (ch == '\n' && lastCh=='\r') {
		                break;
		            }
		            lastCh=ch;
		        }
	        }catch(SocketTimeoutException STE){
	        	//It might be ok. We'll Try to recover from this.
	        }
	        if (buf.size() == 0) {
	            return null;
	        }
	        return buf.toByteArray();
	 }
	 
	public static byte[] readBytes(InputStream inputStream, long bytes) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		int ch;
		long bytesRead = 0;
		try{
			while (bytesRead<bytes && (ch = inputStream.read()) >= 0) {//errr off by 1?
				buf.write(ch);
				bytesRead++;
			}
		}catch(SocketTimeoutException STE){
	        	//It might be ok. We'll Try to recover from this.
				System.out.println(""+bytes+" vs "+ bytesRead +"  "+(bytesRead<bytes));
	    }
		
		if (buf.size() == 0) {
			return new byte[0];
		}
		return buf.toByteArray();
	 }
	 
	
	public static byte[] readChunked(InputStream inputStream) throws IOException {
		ByteArrayOutputStream tmpbuf = new ByteArrayOutputStream();
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		int ch;
		long bytesRead = 0;
		try{
		//while not closed:
			while(true){
				//read out the size.
				int lastCh =0;
				 while ((ch = inputStream.read()) >= 0) {
					 	tmpbuf.write(ch);
			            if (ch == '\n' && lastCh=='\r') {
			                break;
			            }
			            lastCh=ch;
			     }
				 String s = new String(tmpbuf.toByteArray());
				 s = s.replaceAll(("[\\n\\r]"), "");
				 long size = Long.parseLong(s, 16);
				 tmpbuf.reset();
				 //The last-chunk is a regular chunk, with the exception that its length is zero.
				 if(size==0){
					 break;
				 }
				//read content ends with \r\n
				while (bytesRead<size && (ch = inputStream.read()) >= 0) {//errr off by 1?
					buf.write(ch);
					bytesRead++;
				}
				//"read content ends with \r\n"
				ch = inputStream.read();
				ch = inputStream.read();
			}
		}catch(SocketTimeoutException STE){
	        	//It might be ok. We'll Try to recover from this.
	    }
		
		if (buf.size() == 0) {
			return new byte[0];
		}
		return buf.toByteArray();
	 }

	
	
}
