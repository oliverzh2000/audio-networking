/**
 * Interface for reading/writing samples to an imput/output stream.
 *
 * @author Oliver on 3/3/2018
 */
public interface AudioIO {
    /**
     * Reads the next sample from the input stream.
     *
     * @return the next sample
     */
    byte readSample();

    /**
     * Reads the next n samples from the input stream.
     *
     * @param n the number of samples to read
     * @return the next sample
     */
    byte[] readSamples(int n);

    /**
     * Writes the given sample to the output stream.
     *
     * @param sample the sample to write
     */
    void writeSample(byte sample);

    /**
     * Writes the given samples to the output stream.
     *
     * @param samples the samples to write
     */
    void writeSamples(byte[] samples);
}
