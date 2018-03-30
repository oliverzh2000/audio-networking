import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

/**
 * @author Oliver on 3/13/2018
 */
public class FrameIOSim implements FrameIO {
    private static final long delay = 5;
    private static final double chanceOfSuccess = 0.5;

    private static final Random RNG = new Random();

    List<Frame> inBuffer = new LinkedList<>();

    public FrameIOSim() {
    }

    public static void main(String[] args) {
        FrameIOSim frameIO = new FrameIOSim();

        Frame testFrame = new Frame((short) 0, (short) 1, false, false, false, false, new byte[]{0});

        (new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    System.out.println(frameIO.decode());
                }
            }
        })).start();

        while (true) {
            Scanner scanner = new Scanner(System.in);
            String message = scanner.nextLine();
            frameIO.encode(new Frame((short) 0, (short) 1, false, false, false, false, message.getBytes()));
            System.out.println(frameIO.inBuffer.size());
        }
    }

    @Override
    public void encode(Frame frame) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (RNG.nextDouble() < chanceOfSuccess) {
            inBuffer.add(frame);
        }
    }

    @Override
    public Frame decode() {
        while (true) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (inBuffer.size() >= 1) {
                Frame incomingFrame = inBuffer.remove(0);
                if (RNG.nextDouble() < chanceOfSuccess) {
                    return incomingFrame;
                }
            }
        }
    }
}
