package Main;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import TorrentData.Torrent;
import Utils.TorrentParser;


/***
 * UI from Lion's Share
 * @author wiselion
 *
 */
public class UI extends JFrame implements ActionListener{
	//TODO: damn download bar!!
//	private class DownloadBar extends JProgressBar implements TableCellRenderer{
//		DecimalFormat df = new DecimalFormat();
//		public DownloadBar(){
//			super(0,100);
//			df.setMaximumFractionDigits(3);
//		}
//		
//		@Override
//		public Component getTableCellRendererComponent(JTable arg0,Object value, boolean arg2, boolean arg3, int arg4, int arg5) {
//			Torrent t = (Torrent)value;
//			float f = 1.0f-(((float)t.getLeft())/t.totalBytes);
//			if(t.getStatus().equals("Checking files")){
//				this.setName("Checking files "+df.format((float)value*100.0));
//			}
//			this.setName(df.format((float)f*100.0));
//			this.setValue((int)f*100);
//            return this;
//		}
//		
//	}
	
	private static final long serialVersionUID = 1L;
	final JFileChooser fileChooser;
	final JButton play,magnet,stop,open,delete;
	final JSplitPane mainPane;
	public final JTable torrentList;
	final LionShare ls;
	public UI(LionShare atm) {
		this.ls =atm;
		try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());//get look and feel of whatever OS we're using
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {}
		
		
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
		    @Override
		    public void run()
		    {
		        ls.on = false;
		    }
		});
		
		
		
		setSize(400, 300);
		play = new JButton("Play");
		magnet = new JButton("Magnet Link");
		stop = new JButton("Stop");
		open = new JButton("Open");
		delete = new JButton("Delete");
		JPanel topPanel = new JPanel(new GridLayout(1, 5));
		topPanel.add(open);topPanel.add(delete);
		topPanel.add(play);topPanel.add(magnet);topPanel.add(stop);
		
        //Name,size, progress bar, dl down, dl up
        torrentList = new JTable(atm);
        JScrollPane scrollPane = new JScrollPane(torrentList);
//        DownloadBar progressBar=new DownloadBar();
//        progressBar.setStringPainted(true);
//        progressBar.setBackground(Color.GRAY);
//        progressBar.setForeground(Color.GREEN);
////        progressBar.setIndeterminate(true);
//        torrentList.getColumn("Status").setCellRenderer(progressBar);
        torrentList.setFillsViewportHeight(true);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK));
       
        torrentList.setShowGrid(true);
        torrentList.setShowHorizontalLines(true);
        torrentList.setShowVerticalLines(true);
        torrentList.setGridColor(Color.GRAY);
        torrentList.setFillsViewportHeight(true);
        torrentList.setPreferredSize(new Dimension(500, 300));
        
        
        JPanel bottom = new JPanel(){
//            @Override
//            public Dimension getPreferredSize() {
//                return new Dimension(200, 200);
//            }
        }; 
        bottom.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        
        GridBagLayout experimentLayout = new GridBagLayout();
        this.setLayout(experimentLayout);
        
        
        GridBagConstraints c = new GridBagConstraints();
        mainPane = new JSplitPane();
        mainPane.setTopComponent(scrollPane);
        mainPane.setBottomComponent(bottom);
		mainPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.PAGE_START;
//        c.gridheight =1;
        c.gridx=0;
        c.gridy=0;
        c.weightx = 1;
        c.weighty = 0;
        this.add(topPanel, c);
        
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;
//        c.gridheight = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx=0;
        c.gridy=1;
        this.add(mainPane, c);
        open.addActionListener(this);
        stop.addActionListener(this);
        magnet.addActionListener(this);
        delete.addActionListener(this);
        play.addActionListener(this);
        
		fileChooser = new JFileChooser();
		mainPane.setDividerLocation(265);
		this.pack();
		setTitle("Lion's Share");
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

    @Override
    public void actionPerformed(ActionEvent e) {
    	
    	if(e.getSource() == open){ 
	    	int returnVal = fileChooser.showOpenDialog(UI.this);
	
	        if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File file = fileChooser.getSelectedFile();
	            //This is where a real application would open the file.
	            System.out.println("Opening: " + file.getName());
	            try{
	            	Torrent t = TorrentParser.parseTorrentFile(file.getAbsolutePath());
	            	ls.addTorrent(t);
	            }catch(Exception ex){
	            	JOptionPane.showMessageDialog(mainPane, "Invalid torrent file");
	            }
	        } else {
	        	System.out.println("Open command cancelled by user.");
	        }
    	}else if(e.getSource() == magnet){
    		System.out.println("Clicked magent");
    		String ans = JOptionPane.showInputDialog(null, "Magnet Link:");
    		System.out.println("Go fetch: "+ans);
    	}else if(e.getSource() == delete){
    		System.out.println("delete ");
    		System.out.println("Delete "+torrentList.getSelectedRow());
    		ls.deleteTorrentData(ls.getTorrent(torrentList.getSelectedRow()));
			
    		//TODO: Choices...
    	}else if(e.getSource()== stop){
    		System.out.println("stop ");
    		System.out.println("Stop "+torrentList.getSelectedRow());
    		try {
				ls.setTorrentDeactive(ls.getTorrent(torrentList.getSelectedRow()));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
    	}else if(e.getSource() == play){
    		System.out.println("play ");
    		System.out.println("Start "+torrentList.getSelectedRow());
    		//Effectively just reload torrent.
    		try {
				ls.reActivate(ls.getTorrent(torrentList.getSelectedRow()));
			} catch (IOException | NoSuchAlgorithmException e1) {
				e1.printStackTrace();
			}
    		
    		
    	}
    }
    
   
}
