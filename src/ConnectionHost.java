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
    final byte localHost;
    private FrameIO frameIO;
    private List<Connection> connections = new ArrayList<>();
    private List<Ping> pings = new ArrayList<>();

    // retransmission TimerTasks need to return if currently busy sending,
    // otherwise risk buildup of frames.
    private volatile boolean isSending = false;

    private Thread receiver = new Thread(() -> {
        while (true) {
            receive();
        }
    });
    private Thread sender = new Thread(() -> {
        while (true) {
            for (Connection connection : connections) {
                connection.send();
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                return;
            }
        }
    });

    public ConnectionHost(byte localHost, FrameIO frameIO) {
        this.localHost = localHost;
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
//        cm.connections.addConnection(connectionServer);
//
////        connectionServer.addMessageToSendQueue("server 1".getBytes());
////        connectionServer.addMessageToSendQueue("server 2".getBytes());
////        connectionServer.addMessageToSendQueue("server 3".getBytes());
////        connectionServer.addMessageToSendQueue("server 4".getBytes());
////
//        Connection connectionClient = new Connection(cm, (byte) 1, (byte) 0, "CLIENT");
//        cm.connections.addConnection(connectionClient);
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
        Connection client = new Connection(cm, (byte) 10, new Address(55, 20), "Client");
        Connection c2 = new Connection(cm, (byte) 20, new Address(55, 10), "c2");
        cm.startParallelIO();

        cm.addConnection(client);
        cm.addConnection(c2);
        while (!cm.ping((byte) 55, 5, 100, 500)) ;
        Random random = new Random();
        byte[] data = new byte[30];
        random.nextBytes(data);

//        client.addSynToSendQueue();
        client.addMessageToSendQueue(data);
        client.addMessageToSendQueue(data);
        client.addMessageToSendQueue(data);
        client.addMessageToSendQueue(data);
        client.addFinToSendQueue();

        Thread.sleep(4000);

        System.out.println(Arrays.toString(c2.getMessage()));
        System.out.println(Arrays.toString(c2.getMessage()));
        System.out.println(Arrays.toString(c2.getMessage()));
        System.out.println(Arrays.toString(c2.getMessage()));

//        System.out.println();
    }

    /**
     * Receives the next frame from the lower-level {@code FrameIO} and distributes
     * the incoming frame to the child connection that it belongs to.
     * Blocks until the next frame is received.
     */
    public void receive() {
        Frame inFrame = frameIO.decode();
        if (inFrame.dest.host != localHost) {
            return;
        }
        if (inFrame.protocol == Frame.PROTOCOL_CONNECTION) {
            // check for connection requests
            if (inFrame.syn && !inFrame.ack &&
                    !connections.contains(new Connection(this, inFrame.dest.port, inFrame.source))) {
                connections.add(new Connection(this, inFrame.dest.port, inFrame.source));
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
     * If the frame is a {@code PROTOCOL_CONNECTION} it's source address must be that of an active connection.
     * If the frame is a {@code PROTOCOL_PING} it's source address host must be equal to {@code localHost}.
     *
     * @param frame the frame to send
     * @throws IllegalArgumentException if the frame's source address is invalid.
     */
    public synchronized void send(Frame frame) throws IllegalArgumentException {
        if (frame.protocol == Frame.PROTOCOL_CONNECTION) {
            List<Address> activeAddresses = new LinkedList<>();
            for (Connection connection : connections) {
                activeAddresses.add(connection.source);
            }
            if (!activeAddresses.contains(frame.source)) {
                throw new IllegalArgumentException("Source address of PROTOCOL_CONNECTION frame is not from an active connection");
            }
        } else if (frame.protocol == Frame.PROTOCOL_PING && frame.source.host != localHost) {
            throw new IllegalArgumentException("Source address host of PROTOCOL_PING frame is not equal to localHost");
        }
        isSending = true;
        RealTimeAudioIO.getInstance().startOutput();
        frameIO.encode(frame);
        RealTimeAudioIO.getInstance().stopOutput();
        isSending = false;
    }

    public boolean ping(byte targetHost, int nFrames, long frameDelay, long timeout) {
        Ping ping = new Ping(targetHost);
        pings.add(ping);
        return (ping.sendEchoRequests(nFrames, frameDelay, timeout));
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

    /**
     * Adds the given connection to the list of connections the host will serve.
     * This is the only way for a connection to send/receive frames.
     * @param connection the connection to addConnection
     */
    public void addConnection(Connection connection) {
        connections.add(connection);
    }

    /**
     * Remove the given connection from the list of connections the host will serve.
     * The given connection will no longer be able to send and receive frames.
     * @param connection the connection to removeConnection
     */
    public void removeConnection(Connection connection) {
        connections.remove(connection);
    }

    class Ping {
        byte targetHost;
        int repliesReceived;

        public Ping(byte targetHost) {
            this.targetHost = targetHost;
        }

        /**
         * Sends an ping/'echo-request' frame {@code nFrames} times, with {@code frameDelay} milliseconds separation.
         * Waits {@code timeout} milliseconds after the last ping has been sent to decide if the ping
         * has passed or failed.
         *
         * Pass: at least one 'echo-reply' was received within {@code timeout} milliseconds since the last
         * frame was sent.
         *
         * Also prints statistics about the round trip time and percent loss.
         *
         * @return {@code true} if the ping passed. {@code false} otherwise.
         */
        public boolean sendEchoRequests(int nFrames, long frameDelay, long timeout) {
            System.out.printf("PING target host: %d, sending %d data bytes in %d frames\n", targetHost, 8 * nFrames, nFrames);
            try {
                for (byte seq = 0; seq < nFrames; seq++) {
                    // sending timestamp allows stateless computation of round trip time
                    byte[] time = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();
                    Frame echoRequest = new Frame(new Address(localHost, 0), new Address(targetHost, 0),
                            seq, false, false, false, false, false, Frame.PROTOCOL_PING, time);
                    send(echoRequest);
                    Thread.sleep(frameDelay);
                }

                Thread.sleep(timeout);
                double percentageLoss = 100.0 * (nFrames - repliesReceived) / nFrames;
                System.out.printf("--- target host: %d PING statistics ---\n", targetHost);
                System.out.printf("%d frames sent, %d frames received, %.2f%% frame loss\n",
                        nFrames, repliesReceived, percentageLoss);
                pings.remove(this);
                System.out.println(repliesReceived > 0 ? "PING passed" : "PING failed");
                System.out.println();
                return repliesReceived > 0;
            } catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * Receives the given 'echo-reply' from the connectionHost, and saves it to the internal buffer.
         * This method should only be called from the connectionHost.
         *
         * @param echoReply the frame received from the connectionHost.
         */
        public void receive(Frame echoReply) {
            repliesReceived++;
            long roundTripTime = System.currentTimeMillis() - ByteBuffer.wrap(echoReply.payload).getLong();
            System.out.printf("received %d bytes PING from host %d seq=%d time=%dms\n",
                    echoReply.payload.length, echoReply.source.host, echoReply.seq, roundTripTime);
        }
    }
}
