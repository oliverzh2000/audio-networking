import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Simple library for reading and writing raw bytes to a WAV file.
 * The type of audio supported is 44100hz, 8-bit pcm, mono, little-endian.
 *
 * @author Oliver on 3/3/2018
 */
public class WavFileAudioIO implements AudioIO {
    private File inFile;
    private File outFile;

    private ByteArrayInputStream inputStream;
    private ByteArrayOutputStream outputStream;

    private AudioFormat inputFormat;
    // 44100 hz, 8-bit, mono, signed PCM, little Endian.
    private AudioFormat outputFormat =
            new AudioFormat(44100, 8, 1, true, false);

    public WavFileAudioIO(File inFile, File outFile) {
        this.inFile = inFile;
        this.outFile = outFile;

        inputStream = new ByteArrayInputStream(new byte[]{});
        outputStream = new ByteArrayOutputStream();
    }

    public static void main(String[] args) throws Exception {
        File inFile = new File("sound_files/wav_file_io/in.wav");
        File outFile = new File("sound_files/wav_file_io/out.wav");
        WavFileAudioIO aio = new WavFileAudioIO(inFile, outFile);
        aio.readFromDisk();
        aio.writeSamples(new byte[]{0, 1, 2, 3, 4, 5, 5, 5, 10, 15, 20, 25, 30});
        System.out.println(Arrays.toString(aio.readSamples(13)));
        aio.writeToDisk();
    }

    /**
     * Reads the next sample from the internal buffer.
     * Call {@code readFromDisk} first to fill the internal buffer.
     *
     * @return the next sample
     */
    @Override
    public byte readSample() {
        int rawSample = inputStream.read();
        if (inputFormat.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED)) {
            // Convert the unsigned 8-bit unsigned rawSample into signed.
            if (rawSample >= 0) {
                return (byte) (rawSample - 128);
            }
            return (byte) (rawSample - 128);
        }
        return (byte) rawSample;
    }

    /**
     * Reads the next n samples from the internal buffer.
     * Call {@code readFromDisk} first to fill the internal buffer.
     *
     * @param n the number of samples to read
     * @return the next n samples
     */
    public byte[] readSamples(int n) {
        byte[] samples = new byte[n];
        for (int i = 0; i < n; i++) {
            samples[i] = readSample();
        }
        return samples;
    }

    /**
     * Writes the given sample to the internal buffer.
     * Call {@code writeToDisk} to write to disk.
     *
     * @param sample the sample to write
     */
    @Override
    public void writeSample(byte sample) {
        outputStream.write(sample);
    }

    /**
     * Writes the given samples to the internal buffer.
     * Call {@code writeToDisk} to write to disk.
     *
     * @param samples the samples to write
     */
    @Override
    public void writeSamples(byte[] samples) {
        for (byte sample : samples) {
            writeSample(sample);
        }
    }

    /**
     * Reads all the data from input file and stores it in the internal input buffer.
     */
    public void readFromDisk() throws IOException, UnsupportedAudioFileException {
        AudioInputStream ais = AudioSystem.getAudioInputStream(inFile);
        int bytesToRead = ais.available();
        byte[] data = new byte[bytesToRead];
        ais.read(data);
        inputFormat = ais.getFormat();
        inputStream = new ByteArrayInputStream(data);
    }

    /**
     * Writes all data in the internal output buffer to disk.
     */
    public void writeToDisk() {
        try {
            outFile.createNewFile();
            byte[] samples = outputStream.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(samples);
            AudioInputStream ais = new AudioInputStream(bais, outputFormat, samples.length);
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
