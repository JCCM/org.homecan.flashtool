package org.homecan.flashtool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;

public class BootloaderControl {
	
	public final static int BOOTLOADER_VERSION = 0x01;
	
	public final static int HOMECAN_UDP_PORT = 1970;
	public final static int HOMECAN_UDP_PORT_BOOTLOADER = 1971;
	
	public final static int HOMECAN_MSGTYPE_CALL_BOOTLOADER = 127;
	
	public DatagramSocket socketBootloader;
	public DatagramSocket socketApp;
	public Integer pagesize;
	public Integer pages;
	public Boolean connected;
	public int msgNumber;
	private String ip;
	private Integer deviceId;

	public BootloaderControl(String ip, Integer deviceId) {		
		this.connected = false;
		this.msgNumber = 0;
		this.ip = ip;
		this.deviceId = deviceId;
		try {			
			socketApp = new DatagramSocket(HOMECAN_UDP_PORT);			
			socketBootloader = new DatagramSocket(HOMECAN_UDP_PORT_BOOTLOADER);
			socketBootloader.setSoTimeout(3000);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();		
		}		
	}
	
	private void updateMsgNumber(BootloaderMsg msg) {
		msg.setMsgNumber(msgNumber++);		
		if (msgNumber>=256) msgNumber = 0;		
	}

	private void enterBootloader() throws IOException {
		System.out.println("Entering Bootloader ...");
		byte[] buffer = new byte[4];
		buffer[0] = 0x0F;
		buffer[1] = HOMECAN_MSGTYPE_CALL_BOOTLOADER;
		buffer[2] = deviceId.byteValue();
		buffer[3] = 0x00;	
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length,InetAddress.getByName(ip),HOMECAN_UDP_PORT);
		socketApp.send(packet);						
	}
	
	private void identifyDevice() throws IOException {
		BootloaderMsg msg = new BootloaderMsg(deviceId,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_IDENTIFY);				

		System.out.println("identifying device ...");
		updateMsgNumber(msg);
		DatagramPacket p =  msg.getPacket();
		p.setAddress(InetAddress.getByName(ip));
		p.setPort(HOMECAN_UDP_PORT_BOOTLOADER);
		socketBootloader.send(p);				
		byte[] buffer = new byte[4096];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);	
		socketBootloader.receive(packet);
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

	private void flashPage(int page, byte[] data) throws IOException {
		//128 words per page, 512 pages (davon 480 RWW und 32 NRWW)
		System.out.print("transmitting page "+page+" ... ");
		for (int i=0;i<pagesize;i+=BootloaderMsg.DATA_PER_MSG) {				
			BootloaderMsg msg = new BootloaderMsg(deviceId,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_DATA);
			if (i==0) {
				//first msg of page
				msg.setDataCounter(BootloaderMsg.START_OF_MSG_MASK | (pagesize/BootloaderMsg.DATA_PER_MSG-1));
			} else {
				msg.setDataCounter((pagesize-i)/BootloaderMsg.DATA_PER_MSG-1);
			}					
			msg.setData(Arrays.copyOfRange(data, i, i+BootloaderMsg.DATA_PER_MSG));			
			updateMsgNumber(msg);
			DatagramPacket p =  msg.getPacket();
			p.setAddress(InetAddress.getByName(ip));
			p.setPort(HOMECAN_UDP_PORT_BOOTLOADER);
			socketBootloader.send(p);	
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (msg.getDataCounter()==0) {
				//last msg of page, check reply
				byte[] buffer = new byte[8];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socketBootloader.receive(packet);
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

	private void flashSegment(Segment segment) throws IOException {		
		BootloaderMsg msg = new BootloaderMsg(deviceId,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_SET_ADDRESS);
		int startPage = segment.getFirstPage(pagesize);
		int endPage = segment.getLastPage(pagesize);
		byte[] data = new byte[4];
		data[0] = (byte)(startPage/256);
		data[1] = (byte)(startPage&0xFF);
		data[2] = 0;	//TODO check if segment starts always on pagestart

		System.out.print("setting segment address ... ");
		msg.setData(data);
		updateMsgNumber(msg);
		DatagramPacket p =  msg.getPacket();
		p.setAddress(InetAddress.getByName(ip));
		p.setPort(HOMECAN_UDP_PORT_BOOTLOADER);
		socketBootloader.send(p);	
		byte[] buffer = new byte[8];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socketBootloader.receive(packet);
		msg = new BootloaderMsg(packet);
		if (msg.getType()==(BootloaderMsg.MSGTYPE_SET_ADDRESS|BootloaderMsg.MSGTYPE_SUCCESSFULL_RESPONSE)) {
			System.out.println("done ");			
			for (int pg=startPage;pg<=endPage;pg++) {
				flashPage(pg,segment.getPage(pg, pagesize));
			}
		} else {
			System.out.println("unsuccessfull response");
			System.out.println(msg.toString());
			System.exit(0);
		}	
	}

	private void startApplication()
			throws UnknownHostException, IOException {
		//start application
		System.out.print("starting application ... ");
		BootloaderMsg msg = new BootloaderMsg(deviceId,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_START_APP);	
		updateMsgNumber(msg);
		DatagramPacket p =  msg.getPacket();
		p.setAddress(InetAddress.getByName(ip));
		p.setPort(HOMECAN_UDP_PORT_BOOTLOADER);
		socketBootloader.send(p);	
		byte[] buffer = new byte[8];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socketBootloader.receive(packet);
		msg = new BootloaderMsg(packet);
		if (msg.getType()==(BootloaderMsg.MSGTYPE_START_APP|BootloaderMsg.MSGTYPE_SUCCESSFULL_RESPONSE)) {
			System.out.println("done");		    	
		} else {
			System.out.println("unsuccessfull response");
			System.out.println(msg.toString());
			System.exit(0);
		}
	}
	
	public void flash(String hexfile) {
		Vector<Segment> segments;		
		try {	
			enterBootloader();
			wait(2);
			identifyDevice();

			HexFileParser hexParser = new HexFileParser(new File(hexfile));
			segments = hexParser.parseFile();			
			for (Iterator<Segment> i = segments.iterator();i.hasNext();) {
				flashSegment(i.next());								
			}
			startApplication();	
		} catch (FileNotFoundException e) {
			System.out.println("Hexfile "+hexfile+" not found");
		} catch (IOException e) {
			if (e instanceof SocketTimeoutException) {
				System.out.println("Response failed: timeout");				
			} else {
				e.printStackTrace();
			}
		}				
	}
	
	public void changeID(int newDeviceID) {	
		try {
			enterBootloader();
			wait(2);
			identifyDevice();
			BootloaderMsg msg = new BootloaderMsg(deviceId,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_CHANGE_ID);		
			byte[] data = new byte[4];
			data[0] = (byte)newDeviceID;					

			System.out.print("changeing device ID ... ");
			msg.setData(data);
			updateMsgNumber(msg);
			DatagramPacket p =  msg.getPacket();
			p.setAddress(InetAddress.getByName(ip));
			p.setPort(HOMECAN_UDP_PORT_BOOTLOADER);
			socketBootloader.send(p);	
			byte[] buffer = new byte[8];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			socketBootloader.receive(packet);
			msg = new BootloaderMsg(packet);
			if (msg.getType()==(BootloaderMsg.MSGTYPE_CHANGE_ID|BootloaderMsg.MSGTYPE_SUCCESSFULL_RESPONSE)) {
				System.out.println("done ");						
			} else {
				System.out.println("unsuccessfull response");
				System.out.println(msg.toString());
				System.exit(0);
			}
			startApplication();
		} catch (IOException e) {
			if (e instanceof SocketTimeoutException) {
				System.out.println("Response failed: timeout");				
			} else {
				e.printStackTrace();
			}
		}	
	}

	private void wait(int s) {
		try {
			Thread.sleep(1000*s);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
}