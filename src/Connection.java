import java.io.File;
import java.util.*;

/**
 * Implements a reliable and acknowledged duplex communication channel
 * between 2 abstract Ports.
 *
 * @author Oliver on 3/11/2018
 */
public class Connection {
    private static final int maxframeSize = 256;  // bytes
    private static final long resendTimeout = 1000;  // milliseconds
    final short sourcePort;
    final short destPort;
    final String name;
    private ConnectionManager connectionManager;
    private volatile boolean sendSeq = false;
    private volatile boolean receiveSeq = false;
    private List<Frame> inboundFrames = new LinkedList<>();
    private List<Frame> outboundFrames = new LinkedList<>();
    private int framesAddedToQueue = 0;
    private Timer resendTimer = new Timer();
    private TimerTask resendTask;

    public Connection(ConnectionManager connectionManager, short sourcePort, short destPort, String name) {
        this.connectionManager = connectionManager;
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.name = name;
    }

    public Connection(ConnectionManager connectionManager, short sourcePort, short destPort) {
        this(connectionManager, sourcePort, destPort, "NO_NAME");
    }

    public static void main(String[] args) {
        File inFile = new File("sound_files/connection_manager/in.wav");
        File outFile = new File("sound_files/connection_manager/in.wav");
        RealTimeAudioIO audioIO = RealTimeAudioIO.getInstance();
        ManchesterCodec manchesterCodec = new ManchesterCodec(8, audioIO);
        RealTimeFrameIO frameIO = new RealTimeFrameIO(manchesterCodec);
        ConnectionManager connectionManager = new ConnectionManager(frameIO);
    }

    public void addToSendQueue(byte[] data) {
        // slice data into frames
        for (int startIndex = 0; startIndex < data.length; startIndex += maxframeSize) {
            boolean sendSeq = framesAddedToQueue % 2 == 1;
            Frame frame = new Frame(sourcePort, destPort, sendSeq, false, false, false,
                    Arrays.copyOfRange(data, startIndex, Math.min(startIndex + maxframeSize, data.length)));
            outboundFrames.add(frame);
            framesAddedToQueue++;
        }
    }

    public void send() {
        if (!outboundFrames.isEmpty() && sendSeq == outboundFrames.get(0).seq) {
            Frame outgoingFrame = outboundFrames.remove(0);
            connectionManager.send(outgoingFrame);
            System.out.println(System.currentTimeMillis() + " " + name + " sent frame:\n    " + outgoingFrame);

            resendTask = new TimerTask() {
                @Override
                public void run() {
                    if (outgoingFrame.seq == sendSeq) {
                        connectionManager.send(outgoingFrame);
                        System.out.println(System.currentTimeMillis() + " " + name + " resent frame:\n    " + outgoingFrame);
                    }
                }
            };
            resendTimer.schedule(resendTask, resendTimeout, resendTimeout);
        }
    }

    public void receive(Frame inboundFrame) {
        if (inboundFrame.ack) {
            if (inboundFrame.seq == sendSeq) {
                System.out.println(System.currentTimeMillis() + " " + name + " received ACK:\n    " + inboundFrame);
                sendSeq = !sendSeq;
                resendTask.cancel();
            } else {
                System.out.println(System.currentTimeMillis() + " " + name + " ignored ack");
            }
        } else if (inboundFrame.syn) {
            // send a SYN ACK to establish the connection
            Frame synack = new Frame(sourcePort, destPort, false, true, true, false, new byte[]{0});
            connectionManager.send(synack);
            System.out.println(System.currentTimeMillis() + " " + name + " sent SYN-ACK:\n    " + synack);
        } else {
            if (inboundFrame.seq == receiveSeq) {
                System.out.println(System.currentTimeMillis() + " " + name + " received frame:\n    " + inboundFrame);
                inboundFrames.add(inboundFrame);
                receiveSeq = !receiveSeq;
            } else {
                System.out.println(System.currentTimeMillis() + " " + name + " ignored frame\n    " + inboundFrame);
            }
            // Always echo back an ACK of the incoming frame, with the payload removed.
            Frame ack = new Frame(sourcePort, destPort, inboundFrame.seq, inboundFrame.syn,
                    true, false, new byte[]{0});
            connectionManager.send(ack);
            System.out.println(System.currentTimeMillis() + " " + name + " sent ACK:\n    " + ack);
        }
    }

    public void connect() {
        // connection requests start at seq = false, becasue it's the first frame.
        Frame request = new Frame(sourcePort, destPort, false, true, false, false, new byte[]{0});
        connectionManager.send(request);
        System.out.println(System.currentTimeMillis() + " " + name + " sent SYN request:\n    " + request);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof Connection)) return false;
        Connection otherConnection = (Connection) other;
        return otherConnection.sourcePort == sourcePort &&
                otherConnection.destPort == destPort;
    }
}
