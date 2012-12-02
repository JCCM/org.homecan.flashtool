package org.homecan.flashtool;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;


public class ChangeID {

	protected DatagramSocket socket = null;

	private final static int BOOTLOADER_VERSION = 0x01;

	protected Integer pagesize = null;
	protected Integer pages = null;
	protected Boolean connected = false;	
	protected int msgNumber = 0;

	public ChangeID(String ip, Integer port) {
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



	private void changeID(Integer oldDeviceID, int newDeviceID) {	
		try {
			identifyDevice(oldDeviceID);
			BootloaderMsg msg = new BootloaderMsg(oldDeviceID,BootloaderMsg.MSGTYPE_REQUEST|BootloaderMsg.MSGTYPE_CHANGE_ID);		
			byte[] data = new byte[1];
			data[0] = (byte)newDeviceID;					

			System.out.print("changeing device ID ... ");
			msg.setData(data);
			updateMsgNumber(msg);

			socket.send(msg.getPacket());
			byte[] buffer = new byte[8];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			socket.receive(packet);
			msg = new BootloaderMsg(packet);
			if (msg.getType()==(BootloaderMsg.MSGTYPE_CHANGE_ID|BootloaderMsg.MSGTYPE_SUCCESSFULL_RESPONSE)) {
				System.out.println("done ");						
			} else {
				System.out.println("unsuccessfull response");
				System.out.println(msg.toString());
				System.exit(0);
			}	
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Integer oldID = null,newID = null;
		String ip = null;
		Integer port = null;		
		if (args.length!=4) {
			System.out.println("usage: changeID <IP-Address> <port> <oldID> <newID>");
			System.exit(0);
		} else {	
			ip = args[0];			
			try {
				port = new Integer(args[1]);
				oldID = new Integer(args[2]);	
				newID = new Integer(args[3]);
			} catch (NumberFormatException e) {
				System.out.println("usage: changeID <IP-Address> <port> <oldID> <newID>");
				System.exit(0);
			}
		}
		ChangeID ci = new ChangeID(ip,port);		
		ci.changeID(oldID, newID);		
	}
}
