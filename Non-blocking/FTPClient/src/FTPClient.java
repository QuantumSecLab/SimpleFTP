import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Scanner;

public class FTPClient {

    private ByteBuffer outputBuffer;

    private int bufferSize = 1 << 16;

    private SocketChannel clientSocketChannel;

    private String remoteIP;

    private int remotePort;

    private String filePath = "random_bits.bin";

    private FileChannel fileChannel;

    private void exitGracefully() {
        this.closeTheSocketChannel();
        this.closeTheFileChannel();
        System.exit(1);
    }

    private void closeTheSocketChannel() {
        if (this.clientSocketChannel != null) {
            try {
                this.clientSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println(this.clientSocketChannel + "Cannot close the socket channel: " + e.getMessage());
            }
        }
    }

    private void closeTheFileChannel() {
        if (this.fileChannel != null) {
            try {
                this.fileChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println(this.fileChannel + "Cannot close the file channel: " + e.getMessage());
            }
        }
    }

    private void printNumberOfBytesSent(int bytesSent) {
        System.out.println("[" + new Date() + "] " + bytesSent + " byte(s) was(were) sent.");
    }

    public FTPClient() {
        // inform the user of entering the remote IP and port
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please enter the remote IP: ");
        this.remoteIP = scanner.nextLine();
        System.out.println("Please enter the remote port: ");
        this.remotePort = scanner.nextInt();

        // open the socket channel
        try {
            this.clientSocketChannel = SocketChannel.open(new InetSocketAddress(this.remoteIP, this.remotePort));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot open the socket channel.");
            this.exitGracefully();
        }

        // set the socket channel to the non-blocking mode
        try {
            this.clientSocketChannel.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(this.clientSocketChannel + "Cannot set the socket channel to the non-blocking mode.");
            this.exitGracefully();
        }

        // allocate the memory to the output buffer
        this.outputBuffer = ByteBuffer.allocate(this.bufferSize);
    }

    public static void main(String args[]) {
        FTPClient client = new FTPClient();
        client.launch();
    }

    private void launch() {
        System.out.println("The client was successfully started on the port " + this.clientSocketChannel.socket().getLocalPort());
        // open the file
        String fileName = null;
        while (true) {
            // inform the user of entering the file path
            System.out.println("Please enter the file path: ");
            Scanner scanner = new Scanner(System.in);
            this.filePath = scanner.nextLine();

            // check the flag
            if (this.filePath.equals("exit")) {
                break;
            }

            // open the file
            try {
                File file = new File(this.filePath);
                fileName = file.getName();
                this.fileChannel = (new RandomAccessFile(file, "r")).getChannel();
                break;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.err.println("Cannot open the file: " +e.getMessage());
            }
        }

        // put the file name length and the file name into the output buffer
        this.outputBuffer.clear();
        this.outputBuffer.put((byte) (fileName.getBytes(StandardCharsets.US_ASCII).length)); // file name length
        this.outputBuffer.put(fileName.getBytes(StandardCharsets.US_ASCII));

        // switch to the read mode
        this.outputBuffer.flip();

        // send the file name length and the file name
        try {
            this.printNumberOfBytesSent(this.clientSocketChannel.write(this.outputBuffer));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot send data on the client socket channel: " + e.getMessage());
            this.exitGracefully();
        }

        // switch to the write mode
        this.outputBuffer.compact();

        // read the file and send it to the remote endpoint
        while (true) {
            // read the file
            try {
                if ((this.fileChannel.read(outputBuffer)) == -1) {
                    this.closeTheFileChannel();
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Cannot read from the file channel: " +e.getMessage());
                this.closeTheFileChannel();
                break;
            }

            // switch the buffer to the read mode
            this.outputBuffer.flip();

            // send the file to the remote endpoint
            try {
                this.printNumberOfBytesSent(this.clientSocketChannel.write(outputBuffer));
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println(this.clientSocketChannel + "Cannot send the file on the socket channel: " + e.getMessage());
                this.exitGracefully();
            }

            // switch the buffer to the write mode
            this.outputBuffer.compact();
        }

        // switch the buffer to the read mode
        this.outputBuffer.flip();

        // flush the remaining data in the buffer to the socket channel
        while (this.outputBuffer.remaining() != 0) {
            try {
                this.printNumberOfBytesSent(this.clientSocketChannel.write(outputBuffer));
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println(this.clientSocketChannel + "Cannot send the file on the socket channel: " + e.getMessage());
                this.exitGracefully();
            }
        }
        // close all channels
        this.closeTheSocketChannel();
        this.closeTheFileChannel();
    }
}
