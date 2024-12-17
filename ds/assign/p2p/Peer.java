package ds.assign.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import poisson.*;

public class Peer {
    String host;
    int port;
    Set<Double> numbers;
    List<Node> neighbors;
    Node localNode;
    Map<Node, Timestamp> nodes;
    Logger logger;

    // @ TODO
    public final long TIMEOUT = 2 * 60 * 1000; // 2 min timeout

    public Peer(String host, int port) {
        this.host = host;
        this.port = port;

        this.numbers = Collections.newSetFromMap(new ConcurrentHashMap<>());

        this.localNode = new Node(host, port);

        this.nodes = new HashMap<>();
        this.nodes.put(localNode, new Timestamp(System.currentTimeMillis()));
        // System.out.println(nodes.entrySet().toString());

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
            Node node = new Node(neighborHost, neighborPort);
            peer.neighbors.add(node);
            peer.nodes.put(node, new Timestamp(System.currentTimeMillis()));
        }

        System.out.printf("Peer started at %s:%d\n", host, port);

        new Thread(new Server(peer)).start();
        // new Thread(new Client(peer)).start();
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
            Map<Node, Timestamp> receivedMap = (Map<Node, Timestamp>) in.readObject();
            // peer.logger.info("Received set for synchronization: " +
            // receivedMap.toString());

            synchronized (peer.nodes) {
                for (Map.Entry<Node, Timestamp> entry : receivedMap.entrySet()) {
                    Node key = entry.getKey();
                    Timestamp value = entry.getValue();
                    peer.nodes.merge(key, value,
                            (oldValue, newValue) -> newValue.compareTo(oldValue) > 0 ? newValue : oldValue);
                }
                out.writeObject(peer.nodes);
            }

            peer.logger.info("Synchronized map: " + peer.nodes.toString());
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
        double lambda = 4;
        PoissonProcess pp = new PoissonProcess(lambda, new Random(0));
        while (true) {
            try {
                double number = random.nextDouble();
                synchronized (peer.numbers) {
                    peer.numbers.add(number);
                }
                peer.logger.info("Generated number: " + number);

                double t = pp.timeForNextEvent() * 60.0 * 1000.0;
                Thread.sleep((int) t);
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
        double lambda = 2; // 2 per min
        PoissonProcess pp = new PoissonProcess(lambda, new Random(0));
        while (true) {
            try {
                // double t = pp.timeForNextEvent() * 60.0 * 1000.0;
                int t = 2000;

                if (peer.neighbors.isEmpty()) {
                    Thread.sleep((int) t);
                    continue;
                }

                Node neighbor = peer.neighbors.get(random.nextInt(peer.neighbors.size()));
                try (Socket socket = new Socket(neighbor.host, neighbor.port);
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    // push
                    synchronized (peer.nodes) {
                        peer.nodes.put(peer.localNode, new Timestamp(System.currentTimeMillis()));
                        peer.logger.info("Updated Timestamp: " + peer.nodes.get(peer.localNode));
                        out.writeObject(peer.nodes);
                    }

                    // pull
                    @SuppressWarnings("unchecked")
                    Map<Node, Timestamp> receivedMap = (Map<Node, Timestamp>) in.readObject();
                    synchronized (peer.nodes) {
                        for (Map.Entry<Node, Timestamp> entry : receivedMap.entrySet()) {
                            Node key = entry.getKey();
                            Timestamp value = entry.getValue();
                            peer.nodes.merge(key, value,
                                    (oldValue, newValue) -> newValue.compareTo(oldValue) > 0 ? newValue : oldValue);
                        }
                    }

                    /*
                     * @ TODO
                     * remove peer with old timestamp
                     */

                    peer.logger.info("Synchronized with " + neighbor);
                    peer.logger.info("Synchronized map: " + peer.nodes.toString());
                } catch (Exception e) {
                    peer.logger.warning("Failed to synchronize with " + neighbor);
                }

                Thread.sleep((int) t);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class Node implements Serializable {
    String host;
    int port;

    public Node(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Node node = (Node) o;
        return port == node.port && Objects.equals(host, node.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
