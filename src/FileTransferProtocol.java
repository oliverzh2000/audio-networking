import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Oliver on 4/5/2018
 */
public class FileTransferProtocol {
    private String localBasePath;
    private String remoteBasePath;
    private Connection connection;

    public FileTransferProtocol(Connection connection, String localBasePath, String remoteBasePath) {
        this.connection = connection;
        this.localBasePath = localBasePath;
        this.remoteBasePath = remoteBasePath;
    }

    public static void main(String[] args) throws Exception {

        RealTimeAudioIO audioIO = RealTimeAudioIO.getInstance();
        audioIO.start();
        ManchesterCodec lineCodec = new ManchesterCodec(8, audioIO);
        FrameIO frameIO = new RealTimeFrameIO(lineCodec);
        ConnectionHost cm = new ConnectionHost((byte) 55, frameIO);
        Connection server = new Connection(cm, (byte) 20, new Address(55, 10), "server");
        Connection client = new Connection(cm, (byte) 10, new Address(55, 20), "client");
        cm.startParallelIO();
        cm.addConnection(server);
        cm.addConnection(client);

        FileTransferProtocol ftpServer = new FileTransferProtocol(server,
                "C:\\Users\\Oliver\\IdeaProjects\\Audio Networking\\FileTransfers\\server",
                "C:\\Users\\Oliver\\IdeaProjects\\Audio Networking\\FileTransfers\\client");
        FileTransferProtocol ftpClient = new FileTransferProtocol(client,
                "C:\\Users\\Oliver\\IdeaProjects\\Audio Networking\\FileTransfers\\client",
                "C:\\Users\\Oliver\\IdeaProjects\\Audio Networking\\FileTransfers\\server");

        ftpServer.sendFile("file1.txt");
        ftpServer.sendFile("file2.txt");
        ftpServer.sendFile("folder/file3.txt");
        ftpClient.receiveFile();
        ftpClient.receiveFile();
        ftpClient.receiveFile();
    }

    private void sendFile(String filePath) {
        byte[] fileContents = new byte[0];
        try {
            fileContents = Files.readAllBytes(Paths.get(localBasePath, filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        connection.addMessageToSendQueue(("FTP_SEND " + filePath).getBytes());
        connection.addMessageToSendQueue(fileContents);
    }

    public void receiveFile() {
        String header = new String(connection.getMessage());
        byte[] fileContents = connection.getMessage();

        if (header.startsWith("FTP_SEND")) {
            String relativePath = header.substring(header.indexOf(' '));

            Path filePath = Paths.get(localBasePath, relativePath);
            new File(filePath.getParent().toString()).mkdirs();
            try {
                Files.write(filePath, fileContents);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new IllegalArgumentException("invalid message header");
        }
    }
}
