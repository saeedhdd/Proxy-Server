import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;

/**
 * Created by hd on 2017/4/1 AD.
 */
public class Client {

    public static void main(String[] args) throws IOException {
//        Scanner scanner = new Scanner(System.in);
//        String host = scanner.nextLine();

        Socket cliRequest = new Socket("localhost",2000);

        PrintStream printStream1 = new PrintStream( cliRequest.getOutputStream());

        printStream1.println("GET http://www.varzesh3.com/livescores/ HTTP/1.0\r\n");
        Scanner proxyRespone = new Scanner(cliRequest.getInputStream());
        while (proxyRespone.hasNextLine()){
            System.out.println(proxyRespone.nextLine());
        }

    }
}
