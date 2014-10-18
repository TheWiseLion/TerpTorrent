package Utils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Bencoding is simple. Ill just write parser for it here.
 * 2 hours later....
 * I thought it would only take 30 mins...
 * But it here now so enjoy it!
 * Oh crap it needs to be reversible for SHA!
 * ----||NOTES||----
 * Major problem was that java's conversion to string was doing a
 * Irreversible conversion! so i had to leave it as byteString
 * @author wiselion
 *
 */
//TODO: create toBytes() for completeness.
//TODO CONVERT TO LONG
public class Bencoding {
	public static enum Type {String,Dictionary,List,Integer}
	public Type type;
	public List<Bencoding> list;
	public Map<String, Bencoding> dictionary;
	public byte [] byteString;
	public Long integer;
	byte [] tmp; //TODO: not this.
	public Bencoding(){};
	public Bencoding(String s) throws UnsupportedEncodingException{
		this.type=Type.String;
		this.byteString=s.getBytes("UTF-8");
	}
	public Bencoding(long i){
		this.type = Type.Integer;
		this.integer = i;
	}
	
	public static Bencoding cBS(byte [] b){
		Bencoding ben = new Bencoding();
		ben.byteString = b;
		ben.type = Bencoding.Type.String;
		return ben;
	}
	
	public Bencoding(byte[]data){
		try {
			if(data.length<2){
				throw new RuntimeException("Not valid b-encoding");
			}
			
			
			getBencoding(data,0,data.length,this);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static int getBencoding(byte[]data,int start, int len,Bencoding r) throws UnsupportedEncodingException{
		if(data[start]=='i'){
			int dist =-1;
			for(int i=start+1;i<len;i++){
				if(data[i]=='e'){
					dist=i;
					break;
				}
			}
			if(dist==-1){
				throw new RuntimeException("Beneconding failed on integer!");
			}
			byte [] integer = new byte[dist-1-start];
			//src dst
			System.arraycopy(data, start+1, integer, 0, dist-1-start);
			String str = new String(new String(integer,0,integer.length,Charset.forName("UTF-8")));
			r.integer = new Long(str);
			r.type = Type.Integer;
			r.tmp = new byte[dist+1-start];System.arraycopy(data, start, r.tmp, 0, dist+1-start);
			return dist+1-start;//distance 'consumed'
		}else if(data[start]=='l' || data[start]=='d'){
			/* List: l<item1><item2>e
	           Dict: d<string1><item1><string2><item2>e*/
			boolean dict=false;
			r.type = Type.List; 
			int cur = start+1;
			if(data[start]=='d'){
				dict = true;
				r.type = Type.Dictionary;
				r.dictionary = new LinkedHashMap<String,Bencoding>();
			}else{
				r.list=new LinkedList<Bencoding>();
			}
			
			while(cur<len && data[cur]!='e'){
				if(dict){
					//get string
					Bencoding string = new Bencoding();
					cur+=getBencoding(data,cur,len,string);
					//get element
					Bencoding elem = new Bencoding();
					cur+=getBencoding(data,cur,len,elem);
					//put!
					r.dictionary.put(string.getString(),elem);
				}else{
					Bencoding elem = new Bencoding();
					cur+=getBencoding(data,cur,len,elem);
					r.list.add(elem);
				}
			}
			r.tmp = new byte[cur+1-start];System.arraycopy(data, start, r.tmp, 0, cur+1-start);
			return cur+1-start;
		}else{//string
			int dist =-1;
			for(int i=start;i<len;i++){
				if(data[i]==':'){
					dist=i;
					break;
				}
			}
			if(dist==-1){
				throw new RuntimeException("Beneconding failed on String!");
			}
			byte [] integer = new byte[dist-start];
			//src dst
			System.arraycopy(data, start, integer, 0, dist-start);
			int slen = new Integer(new String(integer,0,integer.length,Charset.forName("UTF-8")));
			byte [] string = new byte[slen];
			System.arraycopy(data, dist+1, string, 0, slen);
//			r.string= new String(string,"UTF-8");
			r.byteString = string;
			r.type = Type.String;
			r.tmp = new byte[dist+slen+1-start];System.arraycopy(data, start, r.tmp, 0, dist+slen+1-start);
			return dist+slen+1-start;
		}
	
	}
	
	public byte[] toByteArray(){
		byte [] out = null;
		try {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			OutputStream baos = bo;
			if(this.type==Type.Dictionary){
				baos.write('d');
				for(Entry<String, Bencoding> s:dictionary.entrySet()){
					byte [] bytes = s.getKey().getBytes("UTF-8");
					String l = Long.toString(bytes.length);
				    baos.write(l.getBytes("UTF-8"));
				    baos.write(':');
				    baos.write(bytes);
				    baos.write(s.getValue().toByteArray());
				}
				baos.write('e');
			}else if(this.type==Type.Integer){
				baos.write('i');
				String s = integer.toString();
			    baos.write(s.getBytes("UTF-8"));
				baos.write('e');
			}else if(this.type==Type.List){
				baos.write('l');
				for(Bencoding b: list){
					baos.write(b.toByteArray());
				}
				baos.write('e');
			}else{
				byte [] bytes = byteString;
				String l = Long.toString(bytes.length);
			    baos.write(l.getBytes("UTF-8"));
			    baos.write(':');
			    baos.write(bytes);
			}
		
			baos.close();
			baos.flush();
			out = bo.toByteArray();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}
	
	
	public String getString(){
		return new String(byteString,0,byteString.length,Charset.forName("UTF-8"));
	}
}
