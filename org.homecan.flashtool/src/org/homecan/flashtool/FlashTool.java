package org.homecan.flashtool;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;


public class FlashTool {

	protected DatagramSocket socket = null;

	private final static int BOOTLOADER_VERSION = 0x01;

	protected Integer pagesize = null;
	protected Integer pages = null;
	protected Boolean connected = false;	
	protected int msgNumber = 0;

	public FlashTool(String ip, Integer port) {
		try {			
			socket = new DatagramSocket();
			socket.connect(InetAddress.getByName(ip), port);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();		
		}		
	}

	private void updateMsgNumber(BootloaderMsg msg) {
		msg.setMsgNumber(msgNumber++);		
		if (msgNumber>=256) msgNumber = 0;		
	}

	private void identifyDevice(Integer deviceId) throws IOException {
		BootloaderMsg msg = new BootloaderMsg(deviceId,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_IDENTIFY);				

		System.out.println("identifying device ...");
		updateMsgNumber(msg);
		socket.send(msg.getPacket());				
		byte[] buffer = new byte[8];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		msg = new BootloaderMsg(packet);
		byte[] data = msg.getData();		
		switch (data[1]) {
			case 0:pagesize = 32; break;
			case 1:pagesize = 64; break;
			case 2:pagesize = 128; break;
			case 3:pagesize = 256; break;
		}
		pages = data[2]*256+data[3];
		connected = true;
		System.out.println("Device identified: Bootloader version "+data[0]+" pagesize="+pagesize+" pages="+pages);
		if (data[0]!=BOOTLOADER_VERSION) {
			System.out.println("Incompatible BOOTLOADER version detected!!!");
			System.exit(0);
		}
	}

	private void flashPage(Integer deviceID,int page, byte[] data) throws IOException {
		//128 words per page, 512 pages (davon 480 RWW und 32 NRWW)
		System.out.print("transmitting page "+page+" ... ");
		for (int i=0;i<pagesize;i+=BootloaderMsg.DATA_PER_MSG) {				
			BootloaderMsg msg = new BootloaderMsg(deviceID,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_DATA);
			if (i==0) {
				//first msg of page
				msg.setDataCounter(BootloaderMsg.START_OF_MSG_MASK | (pagesize/BootloaderMsg.DATA_PER_MSG-1));
			} else {
				msg.setDataCounter((pagesize-i)/BootloaderMsg.DATA_PER_MSG-1);
			}					
			msg.setData(Arrays.copyOfRange(data, i, i+BootloaderMsg.DATA_PER_MSG));			
			updateMsgNumber(msg);
			socket.send(msg.getPacket());
			if (msg.getDataCounter()==0) {
				//last msg of page, check reply
				byte[] buffer = new byte[8];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);
				msg = new BootloaderMsg(packet);
				byte[] replyData = msg.getData();
				int replyPage =  replyData[0]*256+replyData[1];
				if (msg.getType()==(BootloaderMsg.MSGTYPE_DATA|BootloaderMsg.MSGTYPE_SUCCESSFULL_RESPONSE) && replyPage==page) {
					System.out.println("done");	
				} else {
					System.out.println("unsuccessfull response");
					System.out.println(msg.toString());
					System.exit(0);
				}
			}			
		}
	}

	private void flashSegment(Integer deviceID, Segment segment) throws IOException {		
		BootloaderMsg msg = new BootloaderMsg(deviceID,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_SET_ADDRESS);
		int startPage = segment.getFirstPage(pagesize);
		int endPage = segment.getLastPage(pagesize);
		byte[] data = new byte[2];
		data[0] = (byte)(startPage/256);
		data[1] = (byte)(startPage&0xFF);				

		System.out.print("setting segment address ... ");
		msg.setData(data);
		updateMsgNumber(msg);
		socket.send(msg.getPacket());			
		byte[] buffer = new byte[8];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		msg = new BootloaderMsg(packet);
		if (msg.getType()==(BootloaderMsg.MSGTYPE_SET_ADDRESS|BootloaderMsg.MSGTYPE_SUCCESSFULL_RESPONSE)) {
			System.out.println("done ");			
			for (int p=startPage;p<=endPage;p++) {
				flashPage(deviceID,p,segment.getPage(p, pagesize));
			}
		} else {
			System.out.println("unsuccessfull response");
			System.out.println(msg.toString());
			System.exit(0);
		}	
	}

	private void flash(Integer deviceId, String hexfile) {
		Vector<Segment> segments;		
		try {	
			identifyDevice(deviceId);

			HexFileParser hexParser = new HexFileParser(new File(hexfile));
			segments = hexParser.parseFile();			
			for (Iterator<Segment> i = segments.iterator();i.hasNext();) {
				flashSegment(deviceId, i.next());								
			}
			//start application
			System.out.print("starting application ... ");
			BootloaderMsg msg = new BootloaderMsg(deviceId,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_START_APP);	
			updateMsgNumber(msg);
			socket.send(msg.getPacket());	

			byte[] buffer = new byte[8];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			socket.receive(packet);
			msg = new BootloaderMsg(packet);
			if (msg.getType()==(BootloaderMsg.MSGTYPE_START_APP|BootloaderMsg.MSGTYPE_SUCCESSFULL_RESPONSE)) {
				System.out.println("done");		    	
			} else {
				System.out.println("unsuccessfull response");
				System.out.println(msg.toString());
				System.exit(0);
			}	
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}				
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Integer id = null;
		String ip = null;
		Integer port = null;
		String hexfile = null;
		if (args.length!=4) {
			System.out.println("usage: FlashTool <IP-Address> <port> <id> <hex-file>");
			System.exit(0);
		} else {	
			ip = args[0];
			hexfile = args[3];
			try {
				port = new Integer(args[1]);
				id = new Integer(args[2]);				
			} catch (NumberFormatException e) {
				System.out.println("usage: FlashTool <IP-Address> <port> <id> <hex-file>");
				System.exit(0);
			}
		}
		FlashTool ft = new FlashTool(ip,port);				
		ft.flash(id,hexfile);		
	}
}
