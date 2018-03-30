/**
 * @author Oliver on 3/8/2018
 */
public class Frame {
    short sourcePort;
    short destPort;
    boolean seq;
    boolean syn;
    boolean ack;
    boolean fin;
    byte[] payload;

    public Frame(short sourcePort, short destPort, boolean seq, boolean syn, boolean ack, boolean fin, byte[] payload) {
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.seq = seq;
        this.ack = ack;
        this.syn = syn;
        this.fin = fin;
        this.payload = payload;
    }

    @Override
    public String toString() {
        String header = "source:" + sourcePort + "  dest:" + destPort + "  flags:";
        for (boolean flag : new boolean[]{seq, syn, ack, fin}) {
            if (flag) {
                header += "1";
            } else {
                header += "0";
            }
        }
//        return header + "  payload (" + payload.length + " bytes): " + new String(payload);
        return header + "  payload (" + payload.length + " bytes)";
    }
}
