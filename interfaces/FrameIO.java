/**
 * @author Oliver on 3/13/2018
 */
public interface FrameIO {
    /**
     * Encodes the given frame to the lower-level {@code LineIO}.
     * Wraps the frame data with Start-of-frame delimiters,
     * headers, checksums, and End-of-frame delimiters (if necessary).
     * <p>
     * Method should not block until all data has been physically written.
     *
     * @param frame the frame to encode
     */
    void encode(Frame frame);

    /**
     * Decodes the next valid frame from the lower-level LineIO.
     * Unwraps the frame data from Start-of-frame delimiters,
     * headers, checksums and End-of-frame delimiters (if necessary).
     *
     * Blocks until the next valid frame is decoded.
     * @return the next valid frame
     */
    Frame decode();
}
