/**
 * @author Oliver on 3/8/2018
 */
public class Frame {
    public static final byte PROTOCOL_CONNECTION = 0;
    public static final byte PROTOCOL_PING = 1;
    byte sourcePort;
    boolean syn;
    boolean ack;
    boolean fin;
    byte destPort;
    byte[] payload;
    byte seq;
    byte protocol;

    public Frame(byte sourcePort, byte destPort, byte seq, boolean syn, boolean ack, boolean fin, byte protocol, byte[] payload) {
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.seq = seq;
        this.ack = ack;
        this.syn = syn;
        this.fin = fin;
        this.protocol = protocol;
        this.payload = payload;
    }

    public Frame(byte sourcePort, byte destPort, byte seq, boolean syn, boolean ack, boolean fin, byte protocol) {
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.seq = seq;
        this.ack = ack;
        this.syn = syn;
        this.fin = fin;
        this.protocol = protocol;
        this.payload = new byte[]{};
    }

    @Override
    public String toString() {
        String header = "source:" + sourcePort + "  dest:" + destPort + "  seq:" + seq + "  flags:";
        for (boolean flag : new boolean[]{syn, ack, fin}) {
            if (flag) {
                header += "1";
            } else {
                header += "0";
            }
        }
        return header + "  payload: " + payload.length + " bytes";
    }
}
