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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import poisson.*;

public class Peer {
    String host;
    int port;
    List<Node> neighbors;
    Node localNode;
    Map<Node, Timestamp> nodes;
    Logger logger;

    public final long TIMEOUT = 10 * 60 * 1000; // 10 min timeout

    public Peer(String host, int port) {
        this.host = host;
        this.port = port;

        this.localNode = new Node(host, port);

        this.nodes = new ConcurrentHashMap<>();
        this.nodes.put(localNode, new Timestamp(System.currentTimeMillis()));

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
        new Thread(new Client(peer)).start();
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

            synchronized (peer.nodes) {
                for (Map.Entry<Node, Timestamp> entry : receivedMap.entrySet()) {
                    Node key = entry.getKey();
                    Timestamp value = entry.getValue();
                    peer.nodes.merge(key, value,
                            (oldValue, newValue) -> newValue.compareTo(oldValue) > 0 ? newValue : oldValue);
                }

                /*
                 * remove peers when timestamps
                 * exceed the defined timeout period
                 */

                for (Map.Entry<Node, Timestamp> entry : peer.nodes.entrySet()) {
                    Node key = entry.getKey();
                    Timestamp value = entry.getValue();
                    Timestamp curTimestamp = new Timestamp(System.currentTimeMillis());

                    if (curTimestamp.getTime() - value.getTime() > peer.TIMEOUT) {
                        peer.nodes.remove(key);
                        peer.neighbors.remove(key);
                        peer.logger.warning("Peer removed: " + key);
                    }
                }

                peer.nodes.put(peer.localNode, new Timestamp(System.currentTimeMillis()));
                // peer.logger.info("Updated Timestamp: " + peer.nodes.get(peer.localNode));
                out.writeObject(peer.nodes);
            }

            peer.logger.info("Synchronized map: " + peer.nodes.keySet());
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
        double lambda = 2; // 2 per min
        PoissonProcess pp = new PoissonProcess(lambda, new Random(0));
        while (true) {
            try {
                double t = pp.timeForNextEvent() * 60.0 * 1000.0;
                // int t = 4 * 1000;

                if (peer.neighbors.isEmpty()) {
                    System.out.println("EMPTY");
                    Thread.sleep((int) t);
                    continue;
                }

                Node neighbor = peer.neighbors.get(random.nextInt(peer.neighbors.size()));

                if (!peer.nodes.containsKey(neighbor)) {
                    peer.neighbors.remove(neighbor);
                    continue;
                }

                try (Socket socket = new Socket(neighbor.host, neighbor.port);
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    /*
                     * push
                     */
                    synchronized (peer.nodes) {
                        peer.nodes.put(peer.localNode, new Timestamp(System.currentTimeMillis()));
                        // peer.logger.info("Updated Timestamp: " + peer.nodes.get(peer.localNode));

                        out.writeObject(peer.nodes);
                    }

                    /*
                     * pull
                     */
                    @SuppressWarnings("unchecked")
                    Map<Node, Timestamp> receivedMap = (Map<Node, Timestamp>) in.readObject();
                    synchronized (peer.nodes) {
                        for (Map.Entry<Node, Timestamp> entry : receivedMap.entrySet()) {
                            Node key = entry.getKey();
                            Timestamp value = entry.getValue();
                            peer.nodes.merge(key, value,
                                    (oldValue, newValue) -> newValue.compareTo(oldValue) > 0 ? newValue : oldValue);
                        }

                        /*
                         * remove peers when timestamps
                         * exceed the defined timeout period
                         */
                        for (Map.Entry<Node, Timestamp> entry : peer.nodes.entrySet()) {
                            Node key = entry.getKey();
                            Timestamp value = entry.getValue();
                            Timestamp curTimestamp = new Timestamp(System.currentTimeMillis());

                            if (curTimestamp.getTime() - value.getTime() > peer.TIMEOUT) {
                                peer.nodes.remove(key);
                                peer.neighbors.remove(key);
                                peer.logger.warning("Peer removed: " + key);
                            }
                        }
                    }

                    peer.logger.info("Synchronized with " + neighbor);
                    peer.logger.info("Synchronized map: " + peer.nodes.keySet());

                } catch (Exception e) {
                    peer.logger.warning("Failed to synchronize with " + neighbor);

                    /*
                     * remove neighbor when timestamps
                     * exceed the defined timeout period
                     */

                    Timestamp neighborTimestamp = peer.nodes.get(neighbor);
                    Timestamp curTimestamp = new Timestamp(System.currentTimeMillis());

                    if (curTimestamp.getTime() - neighborTimestamp.getTime() > peer.TIMEOUT) {
                        peer.nodes.remove(neighbor);
                        peer.neighbors.remove(neighbor);
                        peer.logger.warning("Peer removed: " + neighbor);
                        peer.logger.info("Synchronized map: " + peer.nodes.keySet());
                    }
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
