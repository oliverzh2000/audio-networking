import javax.sound.sampled.*;
import java.nio.ByteBuffer;

/**
 * Simple library for reading and writing raw bytes to the standard audio input/output lines.
 * The type of audio supported is 44100hz, 8-bit, mono, signed PCM, little-endian.
 *
 * @author Oliver on 3/3/2018
 */
public class RealTimeAudioIO implements AudioIO {
    // TODO: different buffer size for input.output
    // 44,100.0 samples per second, 8-bit audio, mono, signed PCM, little Endian
    private static final AudioFormat AUDIO_FORMAT =
            new AudioFormat(44100, 8, 1, true, false);

    // Discards these initial samples because the buffer has not been filled yet.
    // We will keep this number large to be conservative.
    private static final int DATA_LINE_BUFFER_SIZE = 4096;
    private static final int SAMPLES_TO_DISCARD = 4096;
    private static final int INTERNAL_BUFFER_SIZE = 32;
    private static RealTimeAudioIO ourInstance = new RealTimeAudioIO();
    private SourceDataLine outputLine;
    private TargetDataLine inputLine;
    private ByteBuffer inputByteBuffer;
    private ByteBuffer outputByteBuffer;

    /**
     * Initializes the input/output streams.
     * The method blocks until the first 5000 frames have been recorded and discarded.
     * These frames are garbage values because the real recording doesn't
     * start until latency in the buffers have been flushed.
     * <p>
     * **IMPORTANT** Only one instance of {@code PhysicalIO} may be instantiated.
     */
    private RealTimeAudioIO() {
        try {
            DataLine.Info sourceDataLineInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            outputLine = (SourceDataLine) AudioSystem.getLine(sourceDataLineInfo);
            outputLine.open(AUDIO_FORMAT, DATA_LINE_BUFFER_SIZE);

            DataLine.Info targetDataLineInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            inputLine = (TargetDataLine) AudioSystem.getLine(targetDataLineInfo);
            inputLine.open(AUDIO_FORMAT, DATA_LINE_BUFFER_SIZE);
            inputByteBuffer = ByteBuffer.allocate(INTERNAL_BUFFER_SIZE);
            outputByteBuffer = ByteBuffer.allocate(INTERNAL_BUFFER_SIZE);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }

        // Read and discard these first frames because the input buffers have roughly this much latency.
        inputLine.start();
        for (int i = 0; i < SAMPLES_TO_DISCARD; i++) {
            readSample();
        }
        inputLine.stop();
    }

    public static void main(String[] args) throws Exception {
        RealTimeAudioIO audioIO = RealTimeAudioIO.getInstance();
        audioIO.start();

        for (int i = 0; i < 1000; i++) {
            audioIO.writeSample((byte) i);
        }

        audioIO.outputLine.drain();
        audioIO.outputLine.flush();

        while (true) {
            Thread.sleep(100);
        }
    }

    public static RealTimeAudioIO getInstance() {
        return ourInstance;
    }

    @Override
    public byte readSample() {
        if (!inputByteBuffer.hasRemaining()) {
            // buffer needs to be refilled from the input line.
            inputByteBuffer.flip();
            byte[] samplesFromInputLine = new byte[inputByteBuffer.capacity()];
            inputLine.read(samplesFromInputLine, 0, inputByteBuffer.capacity());
            inputByteBuffer.put(samplesFromInputLine);
            inputByteBuffer.flip();
        }
        return inputByteBuffer.get();
    }

    @Override
    public byte[] readSamples(int n) {
        byte[] samples = new byte[n];
        for (int i = 0; i < n; i++) {
            samples[i] = readSample();
        }
        return samples;
    }

    @Override
    public void writeSample(byte sample) {
        outputByteBuffer.put(sample);
        if (!outputByteBuffer.hasRemaining()) {
            // buffer needs to be emptied into the output line.
            outputLine.write(outputByteBuffer.array(), 0, outputByteBuffer.capacity());
            outputByteBuffer.clear();
        }
    }

    @Override
    public void writeSamples(byte[] samples) {
        for (byte sample : samples) {
            writeSample((byte) (sample / 3));
        }
    }

    /**
     * Starts the IO streams. No real-time activity happens before this call.
     */
    public void start() {
        outputLine.start();
        inputLine.start();
    }

    /**
     * Stops the IO streams. No real-time IO activity happens after this call.
     * A stopped line should retain any audio data in its buffer instead of discarding it,
     * so that upon resumption the I/O can continue where it left off, if possible.
     */
    public void stop() {
        outputLine.stop();
        inputLine.stop();
    }

    public void startOutput() {
        outputLine.start();
    }

    public void stopOutput() {
        outputLine.drain();
        outputLine.flush();
        outputLine.stop();
    }

    /**
     * Re-initializes and opens all the IO streams.
     * Only need to call this method if you called close and need to re-open.
     */
    public void open() {
        ourInstance = new RealTimeAudioIO();
    }

    /**
     * Closes the IO streams and frees up any resources.
     */
    public void close() {
        outputLine.close();
        inputLine.close();
    }
}
