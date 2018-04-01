import java.util.ArrayList;
import java.util.List;

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

    // retransmission TimerTasks need to return if currently busy sending,
    // otherwise risk buildup of frames.
    private volatile boolean isSending = false;

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
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
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

//        Connection connectionServer = new Connection(cm, (byte) 0, (byte) 1, "SERVER");
//        cm.liveConnections.add(connectionServer);
//
////        connectionServer.addToSendQueue("server 1".getBytes());
////        connectionServer.addToSendQueue("server 2".getBytes());
////        connectionServer.addToSendQueue("server 3".getBytes());
////        connectionServer.addToSendQueue("server 4".getBytes());
////
//        Connection connectionClient = new Connection(cm, (byte) 1, (byte) 0, "CLIENT");
//        cm.liveConnections.add(connectionClient);
////        connectionClient.addToSendQueue("client 5".getBytes());
////        connectionClient.addToSendQueue("client 6".getBytes());
////        connectionClient.addToSendQueue("client 7".getBytes());
////        connectionClient.addToSendQueue("client 8".getBytes());
////        cm.startParallelIO();
////
//
//        cm.startParallelIO();
//
//        Random random = new Random();
//        byte[] data = new byte[20000];
//        random.nextBytes(data);
//
//        connectionServer.addToSendQueue(data);
//
////        while (true) {
////            Scanner scanner = new Scanner(System.in);
////            String nextLine = scanner.nextLine();
////            connectionServer.addToSendQueue(nextLine.getBytes());
//////            connectionClient.addToSendQueue(nextLine.getBytes());
////        }

        Connection c1 = new Connection(cm, (byte) 0, (byte) 1, "CLIENT");
        c1.addSynToSendQueue();
        c1.addToSendQueue(new byte[]{1, 2, 3});
        c1.addToSendQueue(new byte[]{1, 2, 3, 4});
        cm.startParallelIO();

        cm.liveConnections.add(c1);

    }

    /**
     * Receives the next frame from the lower-level {@code FrameIO} and distributes
     * the incoming frame to the child connection that it belongs to.
     * Blocks until the next frame is received.
     */
    public void receive() {
        Frame inboundFrame = frameIO.decode();

        if (inboundFrame.protocol == Frame.PROTOCOL_CONNECTION) {
            // check for connection requests
            if (inboundFrame.syn && !inboundFrame.ack) {
                Connection connection = new Connection(this, inboundFrame.destPort, inboundFrame.sourcePort);
                liveConnections.add(connection);
                connection.receive(inboundFrame);
            } else if (inboundFrame.fin && inboundFrame.ack) {
                // TODO: Fix logic for fin.
                for (Connection connection : liveConnections) {
                    if (connection.sourcePort == inboundFrame.destPort &&
                            connection.destPort == inboundFrame.sourcePort) {
                        connection.receive(inboundFrame);
                        liveConnections.remove(connection);
                    }
                }
            }
            // redirect remaining incoming frames to their respective live connections
            else {
                for (Connection connection : liveConnections) {
                    if (connection.sourcePort == inboundFrame.destPort &&
                            connection.destPort == inboundFrame.sourcePort) {
                        connection.receive(inboundFrame);
                    }
                }
            }
        } else if (inboundFrame.protocol == Frame.PROTOCOL_PING) {
            // simulates the echo reply
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
        isSending = true;
        RealTimeAudioIO.getInstance().startOutput();
        frameIO.encode(frame);
        RealTimeAudioIO.getInstance().stopOutput();
        isSending = false;
    }

    public boolean isSending() {
        return isSending;
    }

    /**
     * Starts 2 new Threads to run the send and receive code each in a while loop.
     */
    public void startParallelIO() {
        receiver.start();
        sender.start();
    }

    public void add(Connection connection) {
        liveConnections.add(connection);
    }

    public void remove(Connection connection) {
        liveConnections.remove(connection);
    }
}
