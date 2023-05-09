import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FTPServer {

    private Selector selector;

    private ServerSocketChannel serverSocketChannel;

    private int bufferLength = 1 << 16;

    private HashMap<SocketChannel, FileChannel> fileChannels;
    
    private HashMap<SocketChannel, ByteBuffer> inputBuffers;

    private int printNumberOfBytesReceived(int bytesReceived) {
        System.out.println("[" + new Date() + "] " + bytesReceived + " byte(s) was(were) received.");
        return  bytesReceived;
    }

    private void exitGracefully() {
        this.closeTheSelector();
        this.closeTheServerSocketChannel();
        this.closeAllSocketChannels();
        System.exit(1);
    }

    private void closeAllSocketChannels() {
        Set<SocketChannel> socketChannels = inputBuffers.keySet();
        Iterator<SocketChannel> iterator = socketChannels.iterator();
        while (iterator.hasNext()) {
            SocketChannel socketChannel = iterator.next();
            this.closeASocketChannel(socketChannel);
        }
    }

    private void onlyCloseASocketChannel(SocketChannel socketChannel) {
        if (socketChannel != null) {
            try {
                // close the socket channel
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("[" + new Date() + "]" + socketChannel + "Cannot close the current socket channel: " + e.getMessage());
            }
        }
    }

    private void closeASocketChannel(SocketChannel socketChannel) {
        if (socketChannel != null) {
            try {
                // close the socket channel
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("[" + new Date() + "]" + socketChannel + "Cannot close the current socket channel: " + e.getMessage());
            } finally {
                // free resources
                if (this.fileChannels != null) {
                    // close the file channel
                    FileChannel fileChannel = this.fileChannels.get(socketChannel);
                    if (fileChannel != null) {
                        try {
                            fileChannel.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            System.err.println(fileChannel + " Cannot close the file channel: " + e.getMessage());
                        }
                    }

                    // remove the record
                    this.fileChannels.remove(socketChannel);
                }

                if (this.inputBuffers != null) {
                    this.inputBuffers.remove(socketChannel);
                }
            }
        }
    }

    private void closeTheServerSocketChannel() {
        if (this.serverSocketChannel != null) {
            try {
                this.serverSocketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Cannot close the server socket channel: " + e.getMessage());
            }
        }
    }

    private void closeTheSelector() {
        if (this.selector != null) {
            try {
                this.selector.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Cannot close the selector: " + e.getMessage());
            }
        }
    }

    public FTPServer() {
        // inform the user to enter the port number
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please input the port for server: ");
        int port = scanner.nextInt();

        // open the socket channel
        try {
            this.serverSocketChannel = ServerSocketChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot open the server socket channel: " +e.getMessage());
            System.exit(1);
        }

        // bind the server socket to a port
        while (true) {
            try {
                this.serverSocketChannel.bind(new InetSocketAddress("localhost", port));
                break;
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Cannot bind the server on the port " + port + ". Please assign another one: " + e.getMessage());
                port = scanner.nextInt();
            }
        }

        // set the server socket to the non-blocking mode
        try {
            this.serverSocketChannel.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot set the server socket to the non-blocking mode: " +e.getMessage());
            this.closeTheServerSocketChannel();
            System.exit(1);
        }

        // open the selector
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot open the selector: " + e.getMessage());
            this.closeTheServerSocketChannel();
            System.exit(1);
        }

        // register the server socket channel to the selector
        try {
            this.serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
            System.err.println("Cannot register the server socket channel to the selector: " + e.getMessage());
            this.closeTheSelector();
            this.closeTheServerSocketChannel();
            System.exit(1);
        }

        // initialize the `fileChannels` and `inputBuffers`
        this.fileChannels = new HashMap<>();
        this.inputBuffers = new HashMap<>();
    }

    public static void main(String args[]) {
        try {
            FTPServer server = new FTPServer();
            server.launch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void launch() {
        System.out.println("The server was started successfully on the port " + this.serverSocketChannel.socket().getLocalPort());
        while (true) {
            try {
                this.selector.select();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Cannot perform `select()` method on the selector: " + e.getMessage());
                this.exitGracefully();
            }
            Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    this.accept(key);
                } else if (key.isReadable()) {
                    this.readAndWrite(key);
                }
            }
        }
    }

    private int readAndWrite(SelectionKey key) {
        // get the socket channel and the file channel
        SocketChannel socketChannel = (SocketChannel) key.channel();
        if (socketChannel == null) {
            System.err.println("Null socket channel.");
            return StatusCode.Fail;
        }
        FileChannel fileChannel = this.fileChannels.get(socketChannel);

        // open and name the file
        if (fileChannel == null) {
            // read file name
            String fileName;
            if((fileName = readFileName(socketChannel)) == null) {
                System.err.println("Did not read the file name this time.");
                return StatusCode.Fail;
            }

            // add file channel to the map
            try {
                fileChannel = new RandomAccessFile(new File(fileName), "rw").getChannel();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.err.println("Cannot open the file: " + e.getMessage());
            }
            if (fileChannel == null)
            {
                System.err.println("Cannot open the file.");
                return StatusCode.Fail;
            }
            this.fileChannels.put(socketChannel, fileChannel);
        }

        // get the inputBuffer
        ByteBuffer inputBuffer = this.inputBuffers.get(socketChannel);
        if (inputBuffer == null) {
            System.err.println("Null input buffer.");
            this.closeASocketChannel(socketChannel);
            return StatusCode.Fail;
        }

        // read the data from the socket channel
        boolean isEndOfStream = false;
        try {
            // read the data
            if(this.printNumberOfBytesReceived(socketChannel.read(inputBuffer)) == -1) {
                this.onlyCloseASocketChannel(socketChannel);
                isEndOfStream = true;
            }

            // switch to the read mode
            inputBuffer.flip();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(socketChannel + "Cannot read data from the socket channel: " + e.getMessage());
            this.closeASocketChannel(socketChannel);
            return StatusCode.Fail;
        }

        // write received data to the file
        try {
            fileChannel.write(inputBuffer);

            // switch to the write mode
            inputBuffer.compact();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(fileChannel + " Cannot write to the file: " + e.getMessage());
            this.closeASocketChannel(socketChannel);
            return StatusCode.Fail;
        }

        // switch to the read mode
        inputBuffer.flip();

        // flush the remaining data in the inputBuffer to the file
        while (inputBuffer.remaining() != 0) {
            try {
                fileChannel.write(inputBuffer);
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println(fileChannel + " Cannot write to the file: " + e.getMessage());
                this.closeASocketChannel(socketChannel);
                return StatusCode.Fail;
            }
        }

        // switch to the write mode
        inputBuffer.compact();

        // test the flag to judge whether close the file channel
        if (isEndOfStream) {
            this.closeASocketChannel(socketChannel);
        }

        return StatusCode.SUCCESS;
    }

    private int accept(SelectionKey key) {
        SocketChannel socketChannel = null;
        // accept the socket channel
        try {
            socketChannel = ((ServerSocketChannel) key.channel()).accept();
            System.out.println(socketChannel.getRemoteAddress() + " has connected to the server.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Cannot establish the socket channel for the current connection: " + e.getMessage());
            return StatusCode.Fail;
        }

        // set to non-blocking mode
        try {
            socketChannel.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(socketChannel + "Cannot set the current socket channel to the non-blocking mode: " +e.getMessage());
            this.closeASocketChannel(socketChannel);
            return StatusCode.Fail;
        }

        // register the socket channel to the selector
        try {
            socketChannel.register(this.selector, SelectionKey.OP_READ);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
            System.err.println(socketChannel + "Cannot register the current socket channel to the selector: " + e.getMessage());
            this.closeASocketChannel(socketChannel);
            return StatusCode.Fail;
        }

        // add the socket channel to the `fileChannels`
        fileChannels.put(socketChannel, null);

        // bind an input buffer to the socket channel
        ByteBuffer inputBuffer = ByteBuffer.allocate(this.bufferLength);
        if (inputBuffer == null) {
            System.err.println("Cannot allocate the memory to the ByteBuffer.");
            this.closeASocketChannel(socketChannel);
            return StatusCode.Fail;
        }
        this.inputBuffers.put(socketChannel, inputBuffer);

        return StatusCode.SUCCESS;
    }

    private String readFileName(SocketChannel socketChannel) {
        // get the inputBuffer
        ByteBuffer inputBuffer = this.inputBuffers.get(socketChannel);

        // test the inputBuffer
        if (inputBuffer == null) {
            System.err.println("Null input buffer.");
            return null;
        }

        // read the first batch of data from socket input buffer in one go
        try {
            this.printNumberOfBytesReceived(socketChannel.read(inputBuffer));
        } catch (IOException e) {
            e.printStackTrace();

        }

        // switch to the read mode
        inputBuffer.flip();

        // test the file name length
        if (inputBuffer.remaining() < 1)
        {
            // switch to the write mode
            inputBuffer.compact();
            // return a null pointer
            return null;
        }

        // get the file name length
        int fileNameLength = Byte.toUnsignedInt(inputBuffer.get());

        // test the file name
        if (inputBuffer.remaining() < fileNameLength) {
            // put the `fileNameLength` back to the buffer
            inputBuffer.position(inputBuffer.position() - 1);
            // switch to the write mode
            inputBuffer.compact();
            // return a null pointer
            return null;
        }

        // get the file name according to `fileNameLength`
        byte[] fileNameByteArray = new byte[fileNameLength];
        inputBuffer.get(fileNameByteArray, 0, fileNameLength);
        String fileName = new String(fileNameByteArray, StandardCharsets.US_ASCII);

        // switch to the write mode
        inputBuffer.compact();

        return fileName;
    }
}
