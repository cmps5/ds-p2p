package ds.assign.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Peer {
    String host;
    int port;
    Set<Double> numbers;
    List<Neighbor> neighbors;
    Logger logger;

    public Peer(String host, int port) {
        this.host = host;
        this.port = port;
        this.numbers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.neighbors = new ArrayList<>();
        this.logger = Logger.getLogger("logfile");
        try {
            FileHandler handler = new FileHandler("./" + host + "_" + port + "_peer.log", true);
            logger.addHandler(handler);
            SimpleFormatter formatter = new SimpleFormatter();
            handler.setFormatter(formatter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length % 2 != 0) {
            System.out.println("Usage: java Peer <host> <port> [<neighborHost> <neighborPort>...]");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        Peer peer = new Peer(host, port);

        for (int i = 2; i < args.length; i += 2) {
            String neighborHost = args[i];
            int neighborPort = Integer.parseInt(args[i + 1]);
            peer.neighbors.add(new Neighbor(neighborHost, neighborPort));
        }

        System.out.printf("Peer started at %s:%d\n", host, port);

        new Thread(new Server(peer)).start();
        new Thread(new Client(peer)).start();
        new Thread(new Synchronizer(peer)).start();
    }
}

class Server implements Runnable {
    Peer peer;

    public Server(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(peer.port, 10, InetAddress.getByName(peer.host))) {
            peer.logger.info("Server running at " + peer.host + ":" + peer.port);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new Connection(clientSocket, peer)).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Connection implements Runnable {
    Socket clientSocket;
    Peer peer;

    public Connection(Socket clientSocket, Peer peer) {
        this.clientSocket = clientSocket;
        this.peer = peer;
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            @SuppressWarnings("unchecked")
            Set<Double> receivedSet = (Set<Double>) in.readObject();
            peer.logger.info("Received set for synchronization: " + receivedSet);

            synchronized (peer.numbers) {
                peer.numbers.addAll(receivedSet);
                out.writeObject(peer.numbers);
            }

            peer.logger.info("Synchronized set: " + peer.numbers);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Client implements Runnable {
    Peer peer;

    public Client(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        Random random = new Random();
        while (true) {
            try {
                double number = random.nextDouble();
                synchronized (peer.numbers) {
                    peer.numbers.add(number);
                }
                peer.logger.info("Generated number: " + number);
                Thread.sleep(10000); // Generate a number every 10 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class Synchronizer implements Runnable {
    Peer peer;

    public Synchronizer(Peer peer) {
        this.peer = peer;
    }

    @Override
    public void run() {
        Random random = new Random();
        while (true) {
            try {
                if (peer.neighbors.isEmpty()) {
                    Thread.sleep(60000);
                    continue;
                }

                Neighbor neighbor = peer.neighbors.get(random.nextInt(peer.neighbors.size()));
                try (Socket socket = new Socket(neighbor.host, neighbor.port);
                     ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    synchronized (peer.numbers) {
                        out.writeObject(peer.numbers);
                    }

                    @SuppressWarnings("unchecked")
                    Set<Double> receivedSet = (Set<Double>) in.readObject();
                    synchronized (peer.numbers) {
                        peer.numbers.addAll(receivedSet);
                    }

                    peer.logger.info("Synchronized with " + neighbor + ", current set: " + peer.numbers);
                } catch (Exception e) {
                    peer.logger.warning("Failed to synchronize with " + neighbor);
                }

                Thread.sleep(60000); // Synchronize every 1 minute
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class Neighbor {
    String host;
    int port;

    public Neighbor(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
