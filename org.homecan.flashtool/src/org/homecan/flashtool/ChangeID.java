package org.homecan.flashtool;


public class ChangeID {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Integer oldID = null,newID = null;
		String ip = null;
		if (args.length!=3) {
			System.out.println("usage: changeID <IP-Address> <oldID> <newID>");
			System.exit(0);
		} else {	
			ip = args[0];			
			try {				
				oldID = new Integer(args[1]);	
				newID = new Integer(args[2]);
			} catch (NumberFormatException e) {
				System.out.println("usage: changeID <IP-Address> <oldID> <newID>");
				System.exit(0);
			}
		}
		BootloaderControl bootloadercontrol = new BootloaderControl(ip, oldID);
		bootloadercontrol.changeID(newID);
	}
}
