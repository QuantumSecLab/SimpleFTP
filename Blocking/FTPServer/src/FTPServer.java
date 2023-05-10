import java.io.FileOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.Date;

public class FTPServer {
    class Worker implements Runnable {
        Socket socket;
        Worker(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                 FileOutputStream fos = new FileOutputStream(dis.readUTF());) {
                byte[] buffer = new byte[1 << 20];
                int bytesRead = -1;

                System.out.println("Started to receive the file.");

                while ((bytesRead = dis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }

                System.out.println("The file was received successfully.");

                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void launch() {
        Scanner sn = new Scanner(System.in);
        System.out.println("Please enter the IP address you want to use:");
        String ipAddr = sn.nextLine();
        InetAddress hostName;
        try {
            hostName = InetAddress.getByName(ipAddr);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Please enter the port number you want to use: ");
        int port = sn.nextInt();

        try (ServerSocket serverSocket = new ServerSocket(port, 100, hostName)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println(clientSocket.getRemoteSocketAddress() + " connected.");
                (new Thread(new Worker(clientSocket))).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) {
        FTPServer server = new FTPServer();
        server.launch();
    }
}
