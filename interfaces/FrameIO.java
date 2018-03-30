/**
 * @author Oliver on 3/13/2018
 */
public interface FrameIO {
    void encode(Frame frame);

    Frame decode();
}
