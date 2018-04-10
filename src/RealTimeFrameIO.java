import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Oliver on 3/4/2018
 */
class RealTimeFrameIO implements FrameIO {
    private static final byte[] PREAMBLE =
            ByteBuffer.allocate(8).putLong(0b01010101_01010101_01010101_01010101_01010101_01010101_01010101_11010101L).array();
    private static final int SYN_MASK = 0b00000001;
    private static final int ACK_MASK = 0b00000010;
    private static final int FIN_MASK = 0b00000100;
    private static final int BEG_MASK = 0b00010000;
    private static final int END_MASK = 0b00100000;

    private static final int maxFrameLength = 32767;  // max value of short

    private LineCodec lineCodec;
    private RealTimeAudioIO audioIO;

    public RealTimeFrameIO(LineCodec lineCodec) {
        this.lineCodec = lineCodec;
        this.audioIO = RealTimeAudioIO.getInstance();
    }

    public static void main(String[] args) throws Exception {
//        File inFile = new File("sound_files/connection_manager/in.wav");
//        File outFile = new File("sound_files/connection_manager/in.wav");
//        WavFileAudioIO audioIO = new WavFileAudioIO(inFile, outFile);
        RealTimeAudioIO audioIO = RealTimeAudioIO.getInstance();
        ManchesterCodec manchesterCodec = new ManchesterCodec(8, audioIO);
        RealTimeFrameIO frameIO = new RealTimeFrameIO(manchesterCodec);

//        Random rng = new Random(0);
//        byte[] bytes = new byte[256];
//        for (int i = 0; i < 100; i++) {
//            rng.nextBytes(bytes);
//            Frame frame = new Frame((short) 0, (short) 0, false, false, false, false, bytes);
//            frameIO.encode(frame);
//            for (int j = 0; j < 300; j++) {
//                audioIO.writeSamples(new byte[]{0, 0, 0, 0});
//            }
//        }
//        audioIO.writeToDisk();

        audioIO.start();
        System.out.println("decoding start");
        int passed = 0;
        int failed = 0;
        for (int i = 0; i < 1000; i++) {
            if (frameIO.decode() != null) {
                passed++;
            } else {
                failed++;
            }
            System.out.println(passed + " " + failed);
        }
    }

    @Override
    public void encode(Frame frame) {
        // Frame format:
        //                 | Header              | Flags *1 byte*      |                                  | payload optional        |
        // preamble  + SoF | source | dest | seq | syn,ack,fin,beg pad | protocol | pay length | head chk | payload | pay chk | end |
        // 8               | 2      | 2    | 1   | 1b  1b  1b  1b  pad | 1        | 2          | 4        | n       | 4       | 2   |
        if (frame.payload.length > maxFrameLength) {
            throw new IllegalArgumentException("Frame size exceeds " + maxFrameLength + " bytes");
        }
        lineCodec.encodeBytes(PREAMBLE);
        ByteBuffer header = ByteBuffer.allocate(2 + 2 + 1 + 1 + 1 + 2);
        header.put(frame.source.host);
        header.put(frame.source.port);
        header.put(frame.dest.host);
        header.put(frame.dest.port);
        header.put(frame.seq);

        byte flags = 0;
        if (frame.syn) flags |= SYN_MASK;
        if (frame.ack) flags |= ACK_MASK;
        if (frame.fin) flags |= FIN_MASK;
        if (frame.beg) flags |= BEG_MASK;
        if (frame.end) flags |= END_MASK;

        header.put(flags);
        header.put(frame.protocol);
        header.putShort((short) frame.payload.length);

        int headerChecksum = Arrays.hashCode(header.array());
        int payloadChecksum = Arrays.hashCode(frame.payload);
        header.flip();

        ByteBuffer frameBytes;
        if (frame.payload.length > 0) {
            // checksum hashes are 4 bytes long.
            frameBytes = ByteBuffer.allocate(header.capacity() + 4 + frame.payload.length + 4);
        } else {
            frameBytes = ByteBuffer.allocate(header.capacity() + 4);
        }
        frameBytes.put(header);
        frameBytes.putInt(headerChecksum);
        if (frame.payload.length > 0) {
            frameBytes.put(frame.payload);
            frameBytes.putInt(payloadChecksum);
        }
        lineCodec.encodeBytes(frameBytes.array());
        lineCodec.encodeBytes(new byte[]{0, 0});
        for (int i = 0; i < 64; i++) {
            audioIO.writeSample((byte) 0);
        }
    }

    @Override
    public Frame decode() {
        // Frame format:
        //                 | Header              | Flags *1 byte*      |                                  | payload optional        |
        // preamble  + SoF | source | dest | seq | syn,ack,fin,beg pad | protocol | pay length | head chk | payload | pay chk | end |
        // 8               | 2      | 2    | 1   | 1b  1b  1b  1b  pad | 1        | 2          | 4        | n       | 4       | 2   |
        start:
        while (true) {
            int preambleBitsLeft = 32;
            boolean prevPreambleBit = false;
            while (preambleBitsLeft > 0) {
                if (lineCodec.decodeBit() != prevPreambleBit) {
                    preambleBitsLeft--;
                    prevPreambleBit = !prevPreambleBit;
                } else {
                    preambleBitsLeft = 32;
                }
            }
            // search for start of frame delimiter "11" until it's found
            for (int i = 0; i < 64; i++) {
                if (lineCodec.decodeBit() && lineCodec.decodeBit()) {
                    break;
                }
                if (i == 63) {
//                    throw new IllegalStateException("sof not detected");
                    continue start;
                }
            }
//            System.out.println("SOF found");

            ByteBuffer header = ByteBuffer.wrap(lineCodec.decodeBytes(2 + 2 + 1 + 1 + 1 + 2));
//        System.out.println(Arrays.toString(header.array()));
            Address source = new Address(header.get(), header.get());
            Address dest = new Address(header.get(), header.get());
            byte seq = header.get();
            byte flags = header.get();
            boolean syn = (flags & SYN_MASK) != 0;
            boolean ack = (flags & ACK_MASK) != 0;
            boolean fin = (flags & FIN_MASK) != 0;
            boolean beg = (flags & BEG_MASK) != 0;
            boolean end = (flags & END_MASK) != 0;
            byte protocol = header.get();
            short payloadLength = header.getShort();

            int headerChecksum = ByteBuffer.wrap(lineCodec.decodeBytes(4)).getInt();
            if (Arrays.hashCode(header.array()) != headerChecksum) {
                System.out.println("INVALID header checksum");
                continue;
            }
            if (payloadLength > 0) {
                byte[] payload = lineCodec.decodeBytes(payloadLength);
                int payloadChecksum = ByteBuffer.wrap(lineCodec.decodeBytes(4)).getInt();
                if (Arrays.hashCode(payload) != payloadChecksum) {
                    System.out.println("INVALID payload checksum");
                    continue;
                }
                return new Frame(source, dest, seq, syn, ack, fin, beg, end, protocol, payload);
            } else {
                return new Frame(source, dest, seq, syn, ack, fin, beg, end, protocol);
            }
        }
    }
}
