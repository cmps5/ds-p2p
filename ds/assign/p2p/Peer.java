package ds.assign.p2p;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class Peer {
	String host;
	Logger logger;

	public Peer(String hostname) {
		host = hostname;
		logger = Logger.getLogger("logfile");
		try {
			FileHandler handler = new FileHandler("./" + hostname + "_peer.log", true);
			logger.addHandler(handler);
			SimpleFormatter formatter = new SimpleFormatter();
			handler.setFormatter(formatter);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {

		
	}
}

/*
 * @TODO
 */
class Server implements Runnable {
	
	@Override
	public void run() {
		
	}
}


/*
 * @TODO
 */
class Connection implements Runnable {
	
	@Override
	public void run() {
		
	}
}

/*
 * @TODO
 */
class Client implements Runnable {

	@Override
	public void run() {
		
	}
}
