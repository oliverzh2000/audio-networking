/**
 * Interface for encoding and decoding logical digital signals as audio levels.
 *
 * @author Oliver on 3/3/2018
 */
public interface LineCodec {
    /**
     * Encodes the given logical bit and writes it to the output stream.
     *
     * @param bit the logical bit to encode
     */
    void encodeBit(boolean bit);

    /**
     * Encodes the given logical bytes and writes it to the output stream.
     *
     * @param bytes the logical bytes to encode
     */
    void encodeBytes(byte[] bytes);

    /**
     * Decodes the next logical bit read from the input stream.
     * The actual number of samples read will depend on the parameters of the particular
     * implementation, as well as the information in the samples read.
     * **IMPORTANT** This method will BLOCK until {@code n} logical bytes have been decoded.
     *
     * @return the decoded bytes
     */
    boolean decodeBit();

    /**
     * Decodes the next {@code n} logical bytes read from the input stream.
     * The actual number of samples read will depend on the parameters of the particular
     * implementation, as well as the information in the samples read.
     * **IMPORTANT** This method will BLOCK until {@code n} logical bytes have been decoded.
     *
     * @param n the number of logical bytes to decode
     * @return the decoded bytes
     */
    byte[] decodeBytes(int n);
}
