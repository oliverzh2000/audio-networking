import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

/**
 * Library for encoding and decoding signals using Manchester Coding as per IEEE 802.3.
 * Manchester Coding has benefits, including:
 * 1.)  Self clocking. Encoder ensures frequent transitions to allow the decoder to recover the clock.
 * 2.)  No DC component. Because logical values are coded as transitions in voltages
 * and not the voltages themselves, therefore a long run of the same logical value will not
 * produce a long run of constant voltage.
 * Disadvantage is that 2 successive symbols are needed per logical bit, meaning bandwidth efficiency is 50%.
 *
 * @author Oliver on 3/4/2018
 */
public class ManchesterCodec implements LineCodec {
    private AudioIO audioIO;

    private int bitLength;

    // These waveforms will be written to the output stream for logical zeros and ones.
    private byte[] upLong;
    private byte[] upShort;
    private byte[] downLong;
    private byte[] downShort;

    private boolean prevSample = false;  // Initial value is arbitrary. Used for detecting transitions (decoding).
    private boolean prevSymbol = false;  // Initial value is arbitrary. Used for encoding. Logical bit is 2 symbols.
    private int samplesSinceLastTransition = 0;

    public ManchesterCodec(int bitLength, AudioIO audioIO) {
        this.bitLength = bitLength;
        this.audioIO = audioIO;

        upLong = roundedHalfSquareWave(bitLength * 2, false);
        upShort = roundedHalfSquareWave(bitLength, false);
        downLong = roundedHalfSquareWave(bitLength * 2, true);
        downShort = roundedHalfSquareWave(bitLength, true);
    }

    public static void main(String[] args) throws Exception {
//        File inFile = new File("sound_files/manchester_codec/in.wav");
//        File outFile = new File("sound_files/manchester_codec/out.wav");
//        WavFileAudioIO aio = new WavFileAudioIO(inFile, outFile);
        RealTimeAudioIO aio = RealTimeAudioIO.getInstance();
        ManchesterCodec mc = new ManchesterCodec(8, aio);
//        aio.readFromDisk();
        aio.start();

        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 300; j++) {
                mc.encodeBit(random.nextBoolean());
            }
            Thread.sleep(500);
        }

//        System.out.println(Arrays.toString(mc.decodeBytes(1)));

//        aio.writeToDisk();
    }

    private static byte[] roundedHalfSquareWave(int period, boolean isInverted) {
        byte[] halfSquareWave = new byte[period / 2];
        for (int i = 0; i < period / 2; i++) {
            if (!isInverted) {
                halfSquareWave[i] = 127;
            } else {
                halfSquareWave[i] = -128;
            }
        }
        halfSquareWave[0] *= 0.6;
        halfSquareWave[halfSquareWave.length - 1] *= 0.6;
        return halfSquareWave;
    }

    /**
     * Encodes the given logical bit and writes it to the audio output stream.
     * Logical 0 and 1 are represented as downward and upward transitions in audio level.
     * Edges of the transitions are rounded to reduce distortion in real audio systems.
     * The duration of this transition is equal to {@code bitLength}.
     *
     * @param bit the logical bit to encode
     */
    @Override
    public void encodeBit(boolean bit) {
        if (prevSymbol != bit) {
            if (prevSymbol) {
                audioIO.writeSamples(upLong);
            } else {
                audioIO.writeSamples(downLong);
            }
        } else {
            if (prevSymbol) {
                audioIO.writeSamples(upShort);
                audioIO.writeSamples(downShort);
            } else {
                audioIO.writeSamples(downShort);
                audioIO.writeSamples(upShort);
            }
        }
        prevSymbol = bit;
    }

    /**
     * Encodes the given logical bytes and writes it to the audio output stream.
     * Logical 0 and 1 are represented as downward and upward transitions in audio level.
     * Edges of the transitions are rounded to reduce distortion in real audio systems.
     * The duration of this transition is equal to {@code bitLength}.
     *
     * @param bytes the logical bytes to encode
     */
    @Override
    public void encodeBytes(byte[] bytes) {
        BitSet bits = BitSet.valueOf(bytes);
        for (int i = 0; i < bytes.length * 8; i++) {
            encodeBit(bits.get(i));
        }
    }

    /**
     * Decodes the next logical bit read from the input stream.
     * The actual number of samples read will depend on the frequency and distribution of
     * transitions in the input stream, as well as the bit length set.
     * <p>
     * Transitions in the audio are ignored if the previous transition took place less than 3/4
     * of a bit length. Transitions which take place after 3/4 of a bit length are interpreted as
     * logical bits.
     * <p>
     * **IMPORTANT** This method will BLOCK until the next logical bit has been decoded.
     *
     * @return the decoded logical bit
     */
    @Override
    public boolean decodeBit() {
        // Any transition that happens 3/4 of a bit length after the previous transition
        // is considered a logical bit transistion.
        // Any transition that happens before this time
        // is considered a 'mid-bit' transition and ignored.
        int bitTransitionThres = bitLength * 3 / 4;

        while (true) {
            boolean sample = audioIO.readSample() >= 0;
            if (sample != prevSample) {
                if (samplesSinceLastTransition >= bitTransitionThres) {
                    samplesSinceLastTransition = 0;
                    return sample;
                }
                prevSample = sample;
            }
            samplesSinceLastTransition++;
        }
    }

    /**
     * Decodes the next {@code n} logical bytes read from the input stream.
     * The actual number of samples read will depend on the frequency and distribution of
     * transitions in the input stream, as well as the bit length set.
     * <p>
     * Transitions in the audio are ignored if the previous transition took place less than 3/4
     * of a bit length. Transitions which take place after 3/4 of a bit length are interpreted as
     * logical bits.
     * <p>
     * **IMPORTANT** This method will BLOCK until {@code n} logical bytes have been decoded.
     *
     * @return the decoded bytes
     */
    @Override
    public byte[] decodeBytes(int n) {
        BitSet bits = new BitSet();
        for (int i = 0; i < n * 8; i++) {
            bits.set(i, decodeBit());
        }
        // Make sure the trailing zeroes are included.
        bits.set(n * 8, true);
        return Arrays.copyOfRange(bits.toByteArray(), 0, n);
    }
}
