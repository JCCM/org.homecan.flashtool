package org.homecan.flashtool;
import java.net.DatagramPacket;
import java.util.Arrays;


public class BootloaderMsg {

	public static final int MSGTYPE_IDENTIFY = 1;
	public static final int MSGTYPE_SET_ADDRESS = 2;
	public static final int MSGTYPE_DATA = 3;
	public static final int MSGTYPE_START_APP = 4;
	public static final int MSGTYPE_CHIP_ERASE = 5;
	public static final int MSGTYPE_CHANGE_ID = 6;
	
	public static final int MSGTYPE_REQUEST = 0x00;	
	public static final int MSGTYPE_SUCCESSFULL_RESPONSE = 0x40;	
	public static final int MSGTYPE_ERROR_RESPONSE = 0x80;	
	public static final int MSGTYPE_WRONG_NUMBER_REPSONSE = 0xC0;	
	
	public static final int DATA_PER_MSG = 4;
	
	public static final int START_OF_MSG_MASK = 0x80;
	
	protected Integer type = null;	
	protected Integer id = null;
	protected Integer msgNumber = null;
	

	protected Integer dataCounter = null;
	protected byte[] data = null;	

	public BootloaderMsg(Integer id,int msgType) {
		this.id = id;
		type = new Integer(msgType);
		msgNumber = 0;
		dataCounter = 0;
		data = new byte[4];
	}
	
	public BootloaderMsg(DatagramPacket packet) {
		byte[] buffer = packet.getData();
		this.id = new Integer(buffer[0]);
		type = new Integer(buffer[1]);
		msgNumber = new Integer(buffer[2]);;
		dataCounter = new Integer(buffer[3]);
		data = new byte[buffer.length-4];
		for (int i=0;i<buffer.length-4;i++) {	
			data[i] = buffer[i+4];
		}
	}
	
	public void setDataCounter(Integer dc) {
		dataCounter = dc;
	}
		
	public DatagramPacket getPacket() {
		byte[] buffer = new byte[8];
		buffer[0] = id.byteValue();
		buffer[1] = type.byteValue();
		buffer[2] = msgNumber.byteValue();
		buffer[3] = dataCounter.byteValue();
		for (int i=0;i<data.length;i++) {	
			buffer[i+4] = data[i];
		}
        return new DatagramPacket(buffer, buffer.length);
	}	
	
	public Integer getType() {
		return type;
	}

	public Integer getId() {
		return id;
	}

	public Integer getMsgNumber() {
		return msgNumber;
	}

	public Integer getDataCounter() {
		return dataCounter;
	}

	public byte[] getData() {
		return data;
	}
	
	public void setData(byte[] data) {
		this.data = data;
	}
	
	public void setMsgNumber(int msgNumber) {
		this.msgNumber = msgNumber;
	}

	@Override
	public String toString() {
		return "BootloaderMsg [type=" + type + ", id=" + id + ", msgNumber="
				+ msgNumber + ", dataCounter=" + dataCounter + ", data="
				+ Arrays.toString(data) + "]";
	}
	
	
	
}
