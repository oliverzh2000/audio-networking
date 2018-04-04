import java.nio.ByteBuffer;
import java.util.*;

/**
 * Coordinates the sending and receiving of frames between Connections - acts as a host for Connections.
 * This class receives frames from the lower level FrameIO and distributes them to the appropriate child connection.
 * Also synchronizes outbound frames from child connections and writes them to FrameIO.
 *
 * Only one instance should exist per machine.
 *
 * Contains Ping utility, to test the reachability of other hosts,
 * as well as the quality of the link, and round trip time.
 *
 * @author Oliver on 3/11/2018
 */
public class ConnectionHost {
    final byte localHostID;
    private FrameIO frameIO;
    private List<Connection> connections = new ArrayList<>();
    private List<Ping> pings = new ArrayList<>();

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
            for (Connection connection : connections) {
                connection.send();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
        }
    });

    public ConnectionHost(byte localHostID, FrameIO frameIO) {
        this.localHostID = localHostID;
        this.frameIO = frameIO;
    }

    public static void main(String[] args) throws InterruptedException {
        RealTimeAudioIO audioIO = RealTimeAudioIO.getInstance();
        audioIO.start();
        ManchesterCodec lineCodec = new ManchesterCodec(8, audioIO);

        FrameIO frameIO = new RealTimeFrameIO(lineCodec);
//        FrameIOSim frameIO = new FrameIOSim();

        ConnectionHost cm = new ConnectionHost((byte) 55, frameIO);

//        Connection connectionServer = new Connection(cm, (byte) 0, (byte) 1, "SERVER");
//        cm.connections.add(connectionServer);
//
////        connectionServer.addMessageToSendQueue("server 1".getBytes());
////        connectionServer.addMessageToSendQueue("server 2".getBytes());
////        connectionServer.addMessageToSendQueue("server 3".getBytes());
////        connectionServer.addMessageToSendQueue("server 4".getBytes());
////
//        Connection connectionClient = new Connection(cm, (byte) 1, (byte) 0, "CLIENT");
//        cm.connections.add(connectionClient);
////        connectionClient.addMessageToSendQueue("client 5".getBytes());
////        connectionClient.addMessageToSendQueue("client 6".getBytes());
////        connectionClient.addMessageToSendQueue("client 7".getBytes());
////        connectionClient.addMessageToSendQueue("client 8".getBytes());
////        cm.startParallelIO();
////
//
//        cm.startParallelIO();
//
//        Random random = new Random();
//        byte[] data = new byte[20000];
//        random.nextBytes(data);
//
//        connectionServer.addMessageToSendQueue(data);
//
////        while (true) {
////            Scanner scanner = new Scanner(System.in);
////            String nextLine = scanner.nextLine();
////            connectionServer.addMessageToSendQueue(nextLine.getBytes());
//////            connectionClient.addMessageToSendQueue(nextLine.getBytes());
////        }
        // TODO FIX host id/port id address
        Connection c1 = new Connection(cm, (byte) 55, new Address(55, 97), "CLIENT");
        cm.startParallelIO();

        cm.connections.add(c1);
        while (!cm.ping((byte) 55, 5, 100, 500));

        Random random = new Random();
        byte[] data = new byte[300];
        random.nextBytes(data);

        c1.addSynToSendQueue();
        c1.addMessageToSendQueue(data);
        c1.addMessageToSendQueue(data);
        c1.addFinToSendQueue();
    }

    /**
     * Receives the next frame from the lower-level {@code FrameIO} and distributes
     * the incoming frame to the child connection that it belongs to.
     * Blocks until the next frame is received.
     */
    public void receive() {
        Frame inFrame = frameIO.decode();
        if (inFrame.dest.host != localHostID) {
            return;
        }
        if (inFrame.protocol == Frame.PROTOCOL_CONNECTION) {
            // check for connection requests
            if (inFrame.syn && !inFrame.ack) {
                Connection connection = new Connection(this, inFrame.dest.port, inFrame.source);
                if (!connections.contains(connection)) {
                    connections.add(connection);
                    connection.receive(inFrame);
                }
            } else if (inFrame.fin) {
                // TODO: Fix logic for fin.
                Connection finConnection = null;
                for (Connection connection : connections) {
                    if (connection.source.equals(inFrame.dest) &&
                            connection.dest.equals(inFrame.source)) {
                        connection.receive(inFrame);
                        finConnection = connection;
                    }
                }
                if (finConnection != null) {
                    connections.remove(finConnection);
                } else {
                    // ConnectionHost must acknowledge fin on behalf of connection because the
                    // connection has terminated by now.
                    Frame finAck = new Frame(inFrame.dest, inFrame.source, inFrame.seq,
                            false, true, true, false, false, Frame.PROTOCOL_CONNECTION);
                    send(finAck);
                }
            }
            // redirect remaining incoming frames to their respective live connections
            else {
                for (Connection connection : connections) {
                    if (connection.source.equals(inFrame.dest) &&
                            connection.dest.equals(inFrame.source)) {
                        connection.receive(inFrame);
                    }
                }
            }
        } else if (inFrame.protocol == Frame.PROTOCOL_PING) {
            if (!inFrame.ack) {
                // send an echo reply
                Frame echoReply = new Frame(inFrame.dest, inFrame.source, inFrame.seq,
                        false, true, false,false, false, Frame.PROTOCOL_PING, inFrame.payload);
                send(echoReply);
            } else {
                for (Ping ping : pings) {
                    if (ping.targetHost == inFrame.source.host) {
                        ping.receive(inFrame);
                    }
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
        isSending = true;
        RealTimeAudioIO.getInstance().startOutput();
        frameIO.encode(frame);
        RealTimeAudioIO.getInstance().stopOutput();
        isSending = false;
    }

    public boolean ping(byte targetHost, int nFrames, long frameDelay, long timeout) {
        Ping ping = new Ping(targetHost, nFrames, frameDelay, timeout);
        pings.add(ping);
        ping.sendEchoRequests();
        boolean successful = false;
        while (pings.contains(ping)) {
            if (ping.isSuccessful()) {
                successful = true;
            }
            try {
                Thread.sleep(frameDelay);
            } catch (InterruptedException e) {
                return false;
            }
        }
        System.out.println("PING success: " + successful + "\n");
        return successful;
    }

    public void pingTimeout(Ping ping) {
        pings.remove(ping);
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
        connections.add(connection);
    }

    public void remove(Connection connection) {
        connections.remove(connection);
    }

    class Ping {
        byte targetHost;
        int nFrames;
        long timeout;
        long frameDelay;
        List<Frame> inFrames = new LinkedList<>();

        public Ping(byte targetHost, int nFrames, long frameDelay, long timeout) {
            this.targetHost = targetHost;
            this.nFrames = nFrames;
            this.frameDelay = frameDelay;
            this.timeout = timeout;
        }

        public void sendEchoRequests() {
            System.out.printf("PING target host: %d, sending %d data bytes in %d frames\n", targetHost, 8 * nFrames, nFrames);
            for (byte seq = 0; seq < nFrames; seq++) {
                // sending timestamp allows stateless computation of round trip time
                byte[] time = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();
                Frame echoRequest = new Frame(new Address(localHostID, 0), new Address(targetHost, 0),
                        seq, false, false, false, false, false, Frame.PROTOCOL_PING, time);
                send(echoRequest);
                try {
                    Thread.sleep(frameDelay);
                } catch (InterruptedException e) {
                    return;
                }
            }

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.printf("--- target host: %d PING statistics ---\n", targetHost);
                    System.out.printf("%d frames sent, %d frames received, %.2f%% frame loss\n",
                            nFrames, inFrames.size(), 100.0 * (nFrames - inFrames.size()) / nFrames);
                    pingTimeout(Ping.this);
                }
            }, timeout);
        }

        public void receive(Frame echoReply) {
            inFrames.add(echoReply);
            long roundTripTime = System.currentTimeMillis() - ByteBuffer.wrap(echoReply.payload).getLong();
            System.out.printf("received %d bytes PING from host %d seq=%d time=%dms\n",
                    echoReply.payload.length, echoReply.source.host, echoReply.seq, roundTripTime);
        }

        public boolean isSuccessful() {
            return inFrames.size() > 0;
        }
    }
}
