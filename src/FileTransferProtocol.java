import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Class to request and send files and entire directories over a {@code Connection}.
 *
 * @author Oliver on 4/5/2018
 */
public class FileTransferProtocol {
    private Path localBase;
    private Connection connection;

    public FileTransferProtocol(Connection connection, String localBase) {
        this.connection = connection;
        this.localBase = Paths.get(localBase);
    }

    public static void main(String[] args) {
        RealTimeAudioIO audioIO = RealTimeAudioIO.getInstance();
        audioIO.start();
        LineCodec lineCodec = new ManchesterCodec(8, audioIO);
        FrameIO frameIO = new RealTimeFrameIO(lineCodec);
        ConnectionHost cm = new ConnectionHost((byte) 55, frameIO);
        Connection server = new Connection(cm, (byte) 20, new Address(55, 10), "server");
        Connection client = new Connection(cm, (byte) 10, new Address(55, 20), "client");
        cm.startParallelIO();
        cm.addConnection(server);
        cm.addConnection(client);

        FileTransferProtocol ftpServer = new FileTransferProtocol(server,
                "C:\\Users\\Oliver\\IdeaProjects\\Audio Networking\\FileTransfers\\server");
        FileTransferProtocol ftpClient = new FileTransferProtocol(client,
                "C:\\Users\\Oliver\\IdeaProjects\\Audio Networking\\FileTransfers\\client");
        ftpClient.startParallelReceive();
        ftpServer.startParallelReceive();

//        ftpClient.requestFile("folder1\\hello world.txt");
//        ftpClient.requestFile("folder1\\Transmission Control Protocol.html");
        ftpClient.requestDirectory("folder1");
    }

    /**
     * Sends the file specified at {@code path}, by adding 2 messages to the connection's send queue.
     * Message 1 - 'FTP_SEND ' + path
     * Message 2 - raw binary file contents
     * @param path the path of the file to send
     */
    public void sendFile(Path path) {
        try {
            byte[] fileContents = Files.readAllBytes(localBase.resolve(path));
            connection.addMessageToSendQueue(("FTP_SEND " + path).getBytes());
            connection.addMessageToSendQueue(fileContents);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Recursively sends all the files contained in the directory specified at {@code path}.
     * @param path the path of the directory to send
     */
    public void sendDirectory(Path path) {
        try {
            for (Path childPath : Files.newDirectoryStream(localBase.resolve(path))) {
                if (childPath.toFile().isDirectory()) {
//                    sendDirectory(Paths.get(localBasePath).relativize(childPath).toString());
                    sendDirectory(localBase.relativize(childPath));
                } else {
//                    sendFile(Paths.get(localBasePath).relativize(childPath).toString());
                    sendFile(localBase.relativize(childPath));
                }
            }
        } catch (IOException e) {
        }
    }

    /**
     * Sends a request message for the file specified at {@code path} (located on the other side of the connection),
     * by adding 1 messages to the connection's send queue.
     * Message 1 - 'FTP_REQUEST ' + path
     * @param path the path of the file requested.
     */
    public void requestFile(String path) {
        connection.addMessageToSendQueue(("FTP_REQUEST_FILE " + path).getBytes());
    }


    /**
     * Sends a request message for the directory specified at {@code path} (located on the other side of the connection),
     * by adding 1 messages to the connection's send queue.
     * Message 1 - 'FTP_REQUEST ' + path
     * @param path the path of the directory requested.
     */
    public void requestDirectory(String path) {
        connection.addMessageToSendQueue(("FTP_REQUEST_DIR " + path).getBytes());
    }

    /**
     * Runs a while loop that calls {@code receive}.
     */
    public void startParallelReceive() {
        (new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    receive();
                }
            }
        })).start();
    }

    /**
     * Gets the next message/s from the connection, and reacts appropriately,
     * by either saving a file that was sent, or by sending a file that was requested.
     */
    public void receive() {
        String header = new String(connection.getMessage());

        if (header.startsWith("FTP_SEND ")) {
            byte[] fileContents = connection.getMessage();
            Path relativePath = Paths.get(header.substring(header.indexOf(' ') + 1));
            Path filePath = localBase.resolve(relativePath);
            filePath.getParent().toFile().mkdirs();
            try {
                Files.write(filePath, fileContents);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (header.startsWith("FTP_REQUEST_FILE ")) {
            Path relativePath = Paths.get(header.substring(header.indexOf(' ') + 1));
            if (localBase.resolve(relativePath).toFile().exists()) {
                sendFile(relativePath);
            } else {
                connection.addMessageToSendQueue("FTP_REQUEST_FAIL: resource not found".getBytes());
            }
        } else if (header.startsWith("FTP_REQUEST_DIR ")) {
            Path relativePath = Paths.get(header.substring(header.indexOf(' ') + 1));
            if (localBase.resolve(relativePath).toFile().exists()) {
                sendDirectory(relativePath);
            } else {
                connection.addMessageToSendQueue("FTP_REQUEST_FAIL: resource not found".getBytes());
            }
        } else if (header.startsWith("FTP_REQUEST_FAIL")) {
            return;
        } else {
            throw new IllegalArgumentException("invalid message header " + header);
        }
    }
}
