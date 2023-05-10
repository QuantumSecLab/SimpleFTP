import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class FTPClient {
    public static void main(String args[]) {
        Scanner sn = new Scanner(System.in);
        System.out.println("Please input the server IPv4 address: ");
        String serverAddress = sn.nextLine();
        System.out.println("Please input the server port: ");
        int port = sn.nextInt();
        sn.nextLine(); // consume newline character
        System.out.println("Please input the file name: ");
        String fileName = sn.nextLine();

        try (Socket socket = new Socket(serverAddress, port);
             FileInputStream fis = new FileInputStream(fileName);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF(fileName);

            byte buffer[] = new byte[1 << 16];
            int bytesRead = -1;

            System.out.println(fileName + " is being sent.");

            while((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                System.out.println(bytesRead + " bytes were sent.");
            }

            System.out.println("The file " + "\"" + fileName + "\"" + " has been sent successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
