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
    private static final long resendTimeout = 700;  // milliseconds

    final byte sourcePort;
    final byte destPort;
    final String name;
    private ConnectionManager connectionManager;

    private volatile byte sendSeq = 0;
    private volatile byte receiveSeq = 0;

    private List<Frame> inboundFrames = new LinkedList<>();
    private List<Frame> outboundFrames = new LinkedList<>();

    private int framesAddedToQueue = 0;
    private Timer resendTimer = new Timer();
    private TimerTask resendTask;

    public Connection(ConnectionManager connectionManager, byte sourcePort, byte destPort, String name) {
        this.connectionManager = connectionManager;
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.name = name;
    }

    public Connection(ConnectionManager connectionManager, byte sourcePort, byte destPort) {
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
            Frame frame = new Frame(sourcePort, destPort, (byte) framesAddedToQueue, false, false, false, Frame.PROTOCOL_CONNECTION,
                    Arrays.copyOfRange(data, startIndex, Math.min(startIndex + maxframeSize, data.length)));
            outboundFrames.add(frame);
            framesAddedToQueue++;
        }
    }

    public void addSynToSendQueue() {
        Frame connectionRequest = new Frame(sourcePort, destPort, (byte) 0, true, false, false,
                Frame.PROTOCOL_CONNECTION);
        outboundFrames.add(connectionRequest);
        framesAddedToQueue++;
    }

    public void send() {
        if (!outboundFrames.isEmpty() && sendSeq == outboundFrames.get(0).seq) {
            Frame outboundFrame = outboundFrames.remove(0);
            System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " sent frame: ");
            System.out.println(outboundFrame);
            connectionManager.send(outboundFrame);

            resendTask = new TimerTask() {
                @Override
                public void run() {
                    if (connectionManager.isSending()) {
                        // If the ConnectionManager is already busy, sending a frame will cause
                        // future TimerTasks to be piled up into a queue.
                        return;
                    }
                    if (outboundFrame.seq == sendSeq) {
                        System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " resent frame: ");
                        System.out.println(outboundFrame);
                        connectionManager.send(outboundFrame);
                    }
                }
            };
            resendTimer.schedule(resendTask, resendTimeout, resendTimeout);
        }
    }

    public void receive(Frame inboundFrame) {
        if (inboundFrame.ack) {
            if (inboundFrame.seq == sendSeq) {
                System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " received ACK: ");
                System.out.println(inboundFrame);
                sendSeq++;
                resendTask.cancel();
            } else {
                System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " ignored ACK: ");
            }
        } else {
            if (inboundFrame.seq == receiveSeq) {
                if (inboundFrame.syn) {
                    System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " received SYN: ");
                    System.out.println(inboundFrame);
                } else {
                    System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " received frame: ");
                    System.out.println(inboundFrame);
                    inboundFrames.add(inboundFrame);
                }
                receiveSeq++;
            } else {
                System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " ignored frame: ");
                System.out.println(inboundFrame);
            }
            // Always echo back an ACK of the incoming frame, with the payload removed.
            Frame ack = new Frame(sourcePort, destPort, inboundFrame.seq, inboundFrame.syn, true, false,
                    Frame.PROTOCOL_CONNECTION);
            System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " sent ACK: ");
            System.out.println(ack);
            connectionManager.send(ack);
        }
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
