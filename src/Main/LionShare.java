package Main;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import Logic.BasicPeerLogic;
import Logic.TorrentTransmitter;
import NetBase.ManagedConnection;
import NetBase.TorrentServerSocket;
import Primitives.DownloadFile;
import TorrentData.MagnetLink;
import TorrentData.Torrent;
import Trackers.Tracker;

/***
 * Manages connection between UI, and torrent states.
 * TODO: On initialization looks for user data.
 * 
 * @author wiselion
 */
public class LionShare extends AbstractTableModel{
	private static final long serialVersionUID = -143709093895815620L;
	public boolean on =true;
	Set<Torrent> allTorrents = Collections.synchronizedSet(new HashSet<Torrent>());
	Map<Torrent,TorrentTransmitter> activeTorrents = new ConcurrentHashMap<Torrent,TorrentTransmitter>();//Seeding or leeching states
	Set<MagnetLink> preparingTorrents =Collections.synchronizedSet(new HashSet<MagnetLink>()); //Fetching peers, downloading .torrent, searching DHT
	Set<Torrent> inactiveTorrents=Collections.synchronizedSet(new HashSet<Torrent>());//Completed or inactive
	Queue<MagnetLink> links = new ConcurrentLinkedQueue<MagnetLink>();
	Queue<Torrent> newTorrents = new ConcurrentLinkedQueue<Torrent>();
	private final List<MagnetLink> cleaner = new ArrayList<MagnetLink>();
	TorrentServerSocket tss;
	
	public LionShare(){
		try {
			tss = new TorrentServerSocket(6881);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void mainLoop(UI ex) throws IOException, InterruptedException, NoSuchAlgorithmException{
		while(on){
			for(TorrentTransmitter tt : activeTorrents.values()){
				tt.work();
			}
			cleaner.clear();
//			for(MagnetLink ml : preparingTorrents){
//				ml.doWork();
//				if(ml.isComplete()){
//					//TODO: user settings?
//					Torrent t = ml.construct();
//					activeTorrents.add(new TorrentTransmitter(new BasicPeerLogic(),t));
//					allTorrents.add(t);
//					cleaner.add(ml);
//				}
//			}
//			
//			for(MagnetLink ml : cleaner){
//				preparingTorrents.remove(ml);
//			}
			
			///////////////// ADD NEW TORRENTS //////////////////
			while(!newTorrents.isEmpty()){
				Torrent t = newTorrents.poll();
				boolean has = false;
				for(Torrent a : allTorrents){
					if(Arrays.equals(t.hashInfo,a.hashInfo)){
						has = true; break;
					}
				}
				
				for(MagnetLink a : preparingTorrents){
					if(Arrays.equals(t.hashInfo,a.hashInfo)){
						has = true; break;
					}
				}
				
				if(!has){
					allTorrents.add(t);
					activeTorrents.put(t,new TorrentTransmitter(new BasicPeerLogic(),t));
				}
			}
			
			///////////////// Take a nap! //////////////////
			
			
			
			Thread.sleep(10);
			this.fireTableRowsUpdated(0,allTorrents.size()-1);
			//this.fireTableRowsUpdated(0,allTorrents.size());
			for(ManagedConnection mc : tss.getNewConnections()){
				for(Torrent a : allTorrents){
					if(Arrays.equals(mc.getInfoHash(),a.hashInfo)){
						//add connection to torrent.
						a.addConnection(mc);
					}
				}
			}
			
			
		}
		//Shut everything down.
		System.out.println("Shutting down.");
		for(Torrent t : allTorrents){
			Iterator<ManagedConnection> mcs = t.getPeers().iterator();
			while(mcs.hasNext()){
				try{
				ManagedConnection mc = mcs.next();
				mc.shutDown();
				}catch(Exception e){}
				mcs.remove();
			}
			for(Tracker tr : t.getTrackers()){
				tr.close(t);
			}
		}
		tss.close();
		
		
	}
	
	public void addMagnetLink(MagnetLink ml){
		
	}
	
	public void addTorrent(Torrent t){
		newTorrents.add(t);
	}
	
	//--------------------------------
	public void setTorrentDeactive(Torrent t) throws IOException{
		activeTorrents.remove(t);
		inactiveTorrents.add(t);
		//Drop connections
		Iterator<ManagedConnection> mcs = t.getPeers().iterator();
		while(mcs.hasNext()){
			try{
			ManagedConnection mc = mcs.next();
			mc.shutDown();
			}catch(Exception e){}
			mcs.remove();
		}
		for(Tracker tr : t.getTrackers()){
			tr.close(t);
		}
		
		for(DownloadFile f: t.files){
			f.close();
		}
	}
	
	public void deleteTorrentData(Torrent t){
		try{
			setTorrentDeactive(t);
			inactiveTorrents.remove(t);
			allTorrents.remove(t);
			for(DownloadFile f: t.files){
				f.delete();
			}
		}catch(IOException io){
			io.printStackTrace();
		}
	}
	
	public void deleteTorrent(Torrent t) throws IOException{
		setTorrentDeactive(t);
		t.getFile().delete();
	}
	
	public void deleteTorrentAndData(Torrent t) throws IOException{
		deleteTorrentData(t);
		deleteTorrent(t);
	}
	
	public void reActivate(Torrent t) throws NoSuchAlgorithmException, IOException{
		if(!activeTorrents.containsKey(t)){
			t.reload();
			activeTorrents.put(t,new TorrentTransmitter(new BasicPeerLogic(),t));
			inactiveTorrents.remove(t);
		}else{
			System.out.println("Do nothing already active");
		}
	}
	
	
	/////////////////////////UI COLUMNS//////////////////////////////////////////////
	@Override
    public String getColumnName(int column) {
        String name = "??";
        switch (column) {
            case 0:
                name = "Name";
                break;
            case 1:
                name = "Size";
                break;
            case 2:
                name = "Status";
                break;
            case 3:
                name = "Down Speed";
                break;
            case 4:
            	name = "Up Speed";
            	break;
            case 5:
            	name = "Peers";
            	break;
        }
        return name;
    }
	
	@Override
	public int getColumnCount() {
		// TODO Auto-generated method stub
		return 6;
	}

	@Override
	public int getRowCount() {
		return allTorrents.size();
	}
	private DecimalFormat dg;
	{
		dg= new DecimalFormat();
		dg.setMaximumFractionDigits(3);
	}
	
	@Override
	public Object getValueAt(int arg0, int arg1) {
		//return percent dl
		Torrent t = allTorrents.toArray(new Torrent[0])[arg0];//slow
		switch(arg1){
		case 0:
			return t.name;
		case 1:
			String s =null;
			if(t.totalBytes/1024 <999){
				s=""+dg.format(t.totalBytes/1024.0)+" KB";
			}else if(t.totalBytes/(1024*1024)<999){
				s=""+dg.format(t.totalBytes/(1024.0*1024.0))+" MB";
			}else{
				s=""+dg.format(t.totalBytes/(1024.0*1024.0*1024.0))+" GB";
			}
			return s;
		case 2:
			String st;
			float f = 1.0f-(((float)t.getLeft())/t.totalBytes);
			if(t.getStatus().equals("Checking files")){
				st= ("Checking files "+dg.format((float)f*100.0)+"%");
			}else{
				st=(dg.format((float)f*100.0)+"%");
				
			}
			return st;
		case 3:
			return t.getRecentDownRate();// (bytes/ms)=kb/s
		case 4:
			return t.getRecentUpRate();
		case 5:
			return t.getPeers().size();
		}
		return null;
	}
	
	public Torrent getTorrent(int i){
		return allTorrents.toArray(new Torrent[0])[i];
	}
	
	public static void main(String [] args) throws NoSuchAlgorithmException, IOException, InterruptedException{
		LionShare ls = new LionShare();
		final UI ex = new UI(ls);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {

				ex.setVisible(true);
			}
		});
		ls.mainLoop(ex);
	}
	
	
	
}
