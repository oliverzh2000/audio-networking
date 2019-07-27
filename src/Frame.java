/**
 * @author Oliver on 3/8/2018
 */
public class Frame {
    public static final byte PROTOCOL_CONNECTION = 0;
    public static final byte PROTOCOL_PING = 1;
    Address source;
    Address dest;
    byte seq;
    boolean syn;
    boolean ack;
    boolean fin;
    boolean beg;
    boolean end;
    byte protocol;
    byte[] payload;

    public Frame(Address source, Address dest, byte seq,
                 boolean syn, boolean ack, boolean fin, boolean beg, boolean end, byte protocol, byte[] payload) {
        this.source = source;
        this.dest = dest;
        this.seq = seq;
        this.ack = ack;
        this.syn = syn;
        this.fin = fin;
        this.beg = beg;
        this.end = end;
        this.protocol = protocol;
        this.payload = payload;
    }

    public Frame(Address source, Address dest, byte seq,
                 boolean syn, boolean ack, boolean fin, boolean beg, boolean end, byte protocol) {
        this(source, dest, seq, syn, ack, fin, beg, end, protocol, new byte[]{});
    }

    @Override
    public String toString() {
        String header = "source=" + source + "  dest=" + dest + "  seq=" + seq + "  flags=";
        if (ack) {
            header += "|ACK|";
        } else {
            header += "|   |";
        }
        if (syn) {
            header += "SYN|";
        } else {
            header += "   |";
        }
        if (fin) {
            header += "FIN|";
        } else {
            header += "   |";
        }
        if (beg) {
            header += "BEG|";
        } else {
            header += "   |";
        }
        if (end) {
            header += "END|";
        } else {
            header += "   |";
        }
        return header + "  payload (" + payload.length + " bytes): " + new String(payload).replaceAll("\r\n|\n", "");
    }
}
