class Ping {
byte targetHost;
int repliesReceived;

public Ping(byte targetHost) {
    this.targetHost = targetHost;
}

/**
 * Sends an ping/'echo-request' frame {@code nFrames} times, with {@code frameDelay} milliseconds separation.
 * Waits {@code timeout} milliseconds after the last ping has been sent to decide if the ping
 * has passed or failed.
 *
 * Pass: at least one 'echo-reply' was received within {@code timeout} milliseconds since the last
 * frame was sent.
 *
 * Also prints statistics about the round trip time and percent loss.
 *
 * @return {@code true} if the ping passed. {@code false} otherwise.
 */
public boolean sendEchoRequests(int nFrames, long frameDelay, long timeout) {
    System.out.printf("PING target host: %d, sending %d data bytes in %d frames\n", targetHost, 8 * nFrames, nFrames);
    try {
        for (byte seq = 0; seq < nFrames; seq++) {
            // sending timestamp allows stateless computation of round trip time
            byte[] time = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();
            Frame echoRequest = new Frame(new Address(localHost, 0), new Address(targetHost, 0),
                    seq, false, false, false, false, false, Frame.PROTOCOL_PING, time);
            send(echoRequest);
            Thread.sleep(frameDelay);
        }

        Thread.sleep(timeout);
        double percentageLoss = 100.0 * (nFrames - repliesReceived) / nFrames;
        System.out.printf("--- target host: %d PING statistics ---\n", targetHost);
        System.out.printf("%d frames sent, %d frames received, %.2f%% frame loss\n",
                nFrames, repliesReceived, percentageLoss);
        pings.remove(this);
        System.out.println(repliesReceived > 0 ? "PING passed" : "PING failed");
        System.out.println();
        return repliesReceived > 0;
    } catch (InterruptedException e) {
        return false;
    }
}

/**
 * Receives the given 'echo-reply' from the connectionHost, and saves it to the internal buffer.
 * This method should only be called from the connectionHost.
 *
 * @param echoReply the frame received from the connectionHost.
 */
public void receive(Frame echoReply) {
    repliesReceived++;
    long roundTripTime = System.currentTimeMillis() - ByteBuffer.wrap(echoReply.payload).getLong();
    System.out.printf("received %d bytes PING from host %d seq=%d time=%dms\n",
            echoReply.payload.length, echoReply.source.host, echoReply.seq, roundTripTime);
}
}