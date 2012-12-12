package org.homecan.flashtool;


public class FlashTool {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Integer id = null;
		String ip = null;		
		String hexfile = null;
		if (args.length!=3) {
			System.out.println("usage: FlashTool <IP-Address> <id> <hex-file>");
			System.exit(0);
		} else {	
			ip = args[0];
			hexfile = args[2];
			try {				
				id = new Integer(args[1]);				
			} catch (NumberFormatException e) {
				System.out.println("usage: FlashTool <IP-Address> <id> <hex-file>");
				System.exit(0);
			}
		}
		System.out.println("Flashing: "+hexfile);
		BootloaderControl bootloadercontrol = new BootloaderControl(ip, id);
		bootloadercontrol.flash(hexfile);		
	}
}
