import java.util.*;

/**
 * Implements a reliable and message-oriented duplex communication channel
 * between 2 abstract addresses.
 *
 * @author Oliver on 3/11/2018
 */
public class Connection {
    private static final int MAX_FRAME_SIZE = 256;  // bytes
    private static final long RESEND_TIMEOUT = 700;  // milliseconds

    final Address source;
    final Address dest;
    final String name;
    private ConnectionHost connectionHost;

    private volatile byte sendSeq = 0;
    private volatile byte receiveSeq = 0;

    private LinkedList<Frame> inFrames = new LinkedList<>();
    private List<byte[]> inMessages = new LinkedList<>();
    private List<Frame> outFrames = new LinkedList<>();

    private int framesAddedToQueue = 0;
    private Timer resendTimer = new Timer();
    private TimerTask resendTask;

    public Connection(ConnectionHost connectionHost, byte port, Address dest, String name) {
        this.connectionHost = connectionHost;
        this.source = new Address(connectionHost.localHost, port);
        this.dest = dest;
        this.name = name;
    }

    public Connection(ConnectionHost connectionHost, byte port, Address destAddress) {
        this(connectionHost, port, destAddress,
                (new Address(connectionHost.localHost, port)).toString());
    }

    /**
     * Breaks up the given logical message as N frames, and adds them to the
     * internal send queue.
     *
     * N, the number of frames created, is equal to {@code ceil(message.length / MAX_FRAME_SIZE)}.
     *
     * Frames will be physically sent when the connectionHost decides to do so.
     * @param message the logical message to send
     */
    public void addMessageToSendQueue(byte[] message) {
        // slice message into frames
        for (int from = 0; from < message.length; from += MAX_FRAME_SIZE) {
            boolean beg = from == 0;
            boolean end = from + MAX_FRAME_SIZE >= message.length;
            Frame frame = new Frame(source, dest, (byte) framesAddedToQueue,
                    false, false, false, beg, end, Frame.PROTOCOL_CONNECTION,
                    Arrays.copyOfRange(message, from, Math.min(from + MAX_FRAME_SIZE, message.length)));
            outFrames.add(frame);
            framesAddedToQueue++;
        }
    }

    /**
     * Adds a SYN frame to the internal send queue, and returns immediately.
     * Frame will be physically sent when the connectionHost decides to do so.
     */
    public void addSynToSendQueue() {
        Frame synRequest = new Frame(source, dest, (byte) framesAddedToQueue,
                true, false, false, false, false, Frame.PROTOCOL_CONNECTION);
        outFrames.add(synRequest);
        framesAddedToQueue++;
    }

    /**
     * Adds a FIN frame to the internal send queue, and returns immediately.
     * Frame will be physically sent when the connectionHost decides to do so.
     */
    public void addFinToSendQueue() {
        Frame finRequest = new Frame(source, dest, (byte) framesAddedToQueue,
                false, false, true, false, false, Frame.PROTOCOL_CONNECTION);
        outFrames.add(finRequest);
        framesAddedToQueue++;
    }

    /**
     * Sends the next frame from the internal send queue to the connectionHost, if the next
     * queued frame has the next expected sequence number.
     * A retransmission timer with period {@code retransTimeout} is started after the send if an ACK is not
     * received before {@code retransTimeout}.
     *
     * This method should only be called from the connectionHost.
     */
    public void send() {
        if (!outFrames.isEmpty() && sendSeq == outFrames.get(0).seq) {
            Frame outFrame = outFrames.remove(0);
            System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " sent: ");
            System.out.println(outFrame);
            connectionHost.send(outFrame);

            resendTask = new TimerTask() {
                @Override
                public void run() {
                    if (connectionHost.isSending()) {
                        // If the ConnectionHost is already busy, sending a frame will cause
                        // future TimerTasks to be piled up into a queue.
                        return;
                    }
                    if (outFrame.seq == sendSeq) {
                        System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " resent: ");
                        System.out.println(outFrame);
                        connectionHost.send(outFrame);
                    }
                }
            };
            resendTimer.schedule(resendTask, RESEND_TIMEOUT, RESEND_TIMEOUT);
        }
    }


    /**
     * Receives the given frame from the connectionHost.
     * If the frame is an ack for the last frame, retransmission of the last frame will be canceled
     * and the send seq will be incremented.
     * If the frame seq is the one expected next, the frame is added to the internal receive queue.
     * Else, the frame must have a wrong sequence number and is ignored.
     *
     * Finally, if the frame is not an ack, an ack is sent (even if it is not added to the receive buffer).
     *
     * This method should only be called from the connectionHost.
     * @param inFrame the frame to receive from the connectionHost
     */
    public void receive(Frame inFrame) {
//        if (inFrame.ack) {
//            if (inFrame.seq == sendSeq) {
//                System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " received: ");
//                System.out.println(inFrame);
//                sendSeq++;
//                resendTask.cancel();
//            } else {
//                System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " ignored: ");
//                System.out.println(inFrame);
//            }
//        } else {
//            if (inFrame.seq == receiveSeq) {
//                if (!inFrame.syn && !inFrame.fin) {
//                    inFrames.add(inFrame);
//                }
//                System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " received: ");
//                System.out.println(inFrame);
//                receiveSeq++;
//            } else {
//                System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " ignored: ");
//                System.out.println(inFrame);
//            }
//            // Always echo back an ACK of the incoming frame, with the payload removed.
//            Frame ack = new Frame(source, dest, inFrame.seq, inFrame.syn, true, inFrame.fin,
//                    Frame.PROTOCOL_CONNECTION);
//            System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " sent: ");
//            System.out.println(ack);
//            connectionHost.send(ack);
//        }
        if (inFrame.ack && inFrame.seq == sendSeq) {
            System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " received: ");
            System.out.println(inFrame);
            sendSeq++;
            resendTask.cancel();
        } else if (inFrame.seq == receiveSeq) {
            if (!inFrame.syn && !inFrame.fin) {
                inFrames.add(inFrame);
                if (inFrames.getFirst().beg && inFrames.getLast().end) {
                    // Full message has been received. Extract payloads into a message.
                    int messageLength = 0;
                    for (Frame frame : inFrames) {
                        messageLength += frame.payload.length;
                    }
                    byte[] message = new byte[messageLength];
                    int from = 0;
                    for (Frame frame : inFrames) {
                        System.arraycopy(frame.payload, 0, message, from, frame.payload.length);
                        from += frame.payload.length;
                    }
                    inMessages.add(message);
                    inFrames.clear();
                }
            }
            System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " received: ");
            System.out.println(inFrame);
            receiveSeq++;
        } else {
            System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " ignored: ");
            System.out.println(inFrame);
        }
        if (!inFrame.ack) {
            // Always send an ACK if inFrame isn't one.
            Frame ack = new Frame(source, dest, inFrame.seq,
                    inFrame.syn, true, inFrame.fin, false, false, Frame.PROTOCOL_CONNECTION);
            System.out.printf("%-45s", System.currentTimeMillis() + " " + name + " sent: ");
            System.out.println(ack);
            connectionHost.send(ack);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof Connection)) return false;
        Connection otherConnection = (Connection) other;
        return otherConnection.source.equals(source) &&
                otherConnection.dest.equals(dest);
    }
}
