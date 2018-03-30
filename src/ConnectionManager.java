import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Coordinates the sending and receiving of frames between Connections.
 * This class receives frames from the lower level FrameIO and distributes them
 * the the appropriate child connection.
 * Also synchronizes outbound frames from child connections and writes them to FrameIO.
 *
 * @author Oliver on 3/11/2018
 */
public class ConnectionManager {
    private FrameIO frameIO;
    private List<Connection> liveConnections = new ArrayList<>();
    private List<Connection> pendingConnections = new LinkedList<>();

    private Thread receiver = new Thread(() -> {
        while (true) {
            receive();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
        }
    });
    private Thread sender = new Thread(() -> {
        while (true) {
            for (Connection connection : liveConnections) {
                connection.send();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    });

    public ConnectionManager(FrameIO frameIO) {
        this.frameIO = frameIO;
    }

    public static void main(String[] args) throws InterruptedException {
        RealTimeAudioIO audioIO = RealTimeAudioIO.getInstance();
        audioIO.start();
        ManchesterCodec lineCodec = new ManchesterCodec(8, audioIO);

        FrameIO frameIO = new RealTimeFrameIO(lineCodec);
//        FrameIOSim frameIO = new FrameIOSim();

        ConnectionManager cm = new ConnectionManager(frameIO);

        Connection connectionServer = new Connection(cm, (short) 0, (short) 1, "SERVER");
        cm.liveConnections.add(connectionServer);

//        connectionServer.addToSendQueue("server 1".getBytes());
//        connectionServer.addToSendQueue("server 2".getBytes());
//        connectionServer.addToSendQueue("server 3".getBytes());
//        connectionServer.addToSendQueue("server 4".getBytes());
//
        Connection connectionClient = new Connection(cm, (short) 1, (short) 0, "CLIENT");
        cm.liveConnections.add(connectionClient);
//        connectionClient.addToSendQueue("client 5".getBytes());
//        connectionClient.addToSendQueue("client 6".getBytes());
//        connectionClient.addToSendQueue("client 7".getBytes());
//        connectionClient.addToSendQueue("client 8".getBytes());
//        cm.startParallelIO();
//

        cm.startParallelIO();

        Random random = new Random();
        byte[] data = new byte[20000];
        random.nextBytes(data);

        connectionServer.addToSendQueue(data);

//        while (true) {
//            Scanner scanner = new Scanner(System.in);
//            String nextLine = scanner.nextLine();
//            connectionServer.addToSendQueue(nextLine.getBytes());
////            connectionClient.addToSendQueue(nextLine.getBytes());
//        }
    }

    /**
     * Receives the next frame from the lower-level {@code FrameIO} and distributes
     * the incoming frame to the child connection that it belongs to.
     * Blocks until the next frame is received.
     */
    public void receive() {
        Frame incomingFrame = frameIO.decode();
        // check for newly granted connections.
        if (incomingFrame.syn && incomingFrame.ack) {
            for (Connection pendingConnection : pendingConnections) {
                if (pendingConnection.sourcePort == incomingFrame.destPort &&
                        pendingConnection.destPort == incomingFrame.sourcePort) {
                    pendingConnections.remove(pendingConnection);
                    liveConnections.add(pendingConnection);
                }
            }
        }
        // check for connection requests
        else if (incomingFrame.syn) {
            Connection connection = new Connection(this, incomingFrame.destPort, incomingFrame.sourcePort);
            liveConnections.add(connection);
            connection.receive(incomingFrame);
        }
        // redirect remaining incoming frames to their respective live connections
        else {
            for (Connection connection : liveConnections) {
                if (connection.sourcePort == incomingFrame.destPort &&
                        connection.destPort == incomingFrame.sourcePort) {
                    connection.receive(incomingFrame);
                }
            }
        }
    }

    /**
     * Sends the given frame to the lower-level {@code FrameIO} for encoding.
     * Used by child connections to send frames.
     * Blocks until the entire frame has been written.
     *
     * @param frame the frame to send
     */
    public synchronized void send(Frame frame) {
        RealTimeAudioIO.getInstance().startOutput();
        frameIO.encode(frame);
        RealTimeAudioIO.getInstance().stopOutput();
    }

    /**
     * Starts 2 new Threads to run the send and receive code each in a while loop.
     */
    public void startParallelIO() {
        receiver.start();
        sender.start();
    }
}
