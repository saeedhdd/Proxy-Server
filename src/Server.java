import org.junit.internal.runners.statements.RunAfters;
import sun.misc.Cache;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.*;


/**
 * Created by hd on 2017/3/31 AD.
 */
public class Server {
    private static Map<String, String> Cache = new HashMap<>();


    public static synchronized Map<String, String> getCache () {
        return Cache;
    }

    private static Object lock = new Object();
    public static void saveCache() throws IOException {
        synchronized (lock) {
            try( ObjectOutputStream objectOutputStream = new ObjectOutputStream( new FileOutputStream("src/cache.dat"))){
                   objectOutputStream.writeObject(Server.Cache);
            }
            System.out.println("Cache.dat has been updated .");
        }
    }

    public static void loadCache()  {
        synchronized (lock) {
            try(ObjectInputStream objectinputStream = new ObjectInputStream(new FileInputStream("src/cache.dat"))){

                Cache = (Map) objectinputStream.readObject();
                System.out.println("Cached data Loaded : "+ Cache.size() + " Web Pages");

            }catch (FileNotFoundException e) {
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);



    public static  void main(String[] args) throws IOException {
        try {
            loadCache();
            for (String key:Cache.keySet()) {

                String[] hostId = ClientProccess.parse("GET "+key+" HTTP/1.1");
                Server.scheduledExecutorService.scheduleAtFixedRate(new Updateor(hostId[0], hostId[1]),0, 2, TimeUnit.MINUTES);
            }



            ServerSocket serverSocket = new ServerSocket(2000);
            System.out.println("Server Started : localhost 2000");
            while (true) {
                try {
                    Socket serviceSocket = serverSocket.accept();
                    System.out.println("Client Connected : "+serviceSocket.getRemoteSocketAddress());
                    Thread clientThread = new Thread(new ClientProccess(serviceSocket));
                    clientThread.start();

                } catch (IOException e) {
                    e.printStackTrace();
                    throw new Exception();
                }
            }
        }catch (Exception e){}

    }


}


class ClientProccess implements Runnable{
    Socket serviceSocket ;



    public static String sendToRemote(String host, String sDom , boolean conditionalGET , String oldResponse) throws IOException {
        Socket clientToRemote = new Socket(host.substring(7, host.length()-1),80);
        PrintStream printStream1 = new PrintStream( clientToRemote.getOutputStream());
        if (conditionalGET) {
            String date = returnDate(oldResponse).replace("Date: ", "If-Modified-Since: ");
            printStream1.println("GET /" + sDom + " HTTP/1.0\r\n" +
                    "Host: " + host.substring(7, host.length()-1) + "\r\n" +
                    date+
                    "Connection: close\r\n");
        }else {
            printStream1.println("GET /" + sDom + " HTTP/1.0\r\n" +
                    "Host: " + host.substring(7, host.length()-1) + "\r\n" +
                    "Cookie: d76743f062ab006d7f1c2bd3f660a30ec1491229991"+
                    "Connection: close\r\n");
        }
        Scanner scanner1 = new Scanner(clientToRemote.getInputStream());
        StringBuffer res = new StringBuffer();

        int code = 0;
        if (scanner1.hasNextLine()) {
            String firstLine = scanner1.nextLine();
            code = findCode(firstLine);
            res.append(firstLine + "\n");
        }
        while (scanner1.hasNextLine()) {
            res.append(scanner1.nextLine() + "\n");
        }
        if (conditionalGET && code == 304) {
            System.out.println("Address Updated : not Changed :"+host + sDom);
            return oldResponse;

        }else
        {
            System.out.println("Fetched from remote :  " + host+sDom);
            return res.toString();
        }

    }

    private static int findCode(String firstLine) {
        if (firstLine.contains("HTTP/1.")){
            String[] strParts = firstLine.split(" ");
            return  Integer.parseInt(strParts[1]);
        }
        return 000;
    }


    public static void checkValidity(String cliReq){
        String[] parts=cliReq.split(" ");
        if (parts.length!=3){printError();}
        if(!parts[0].equals("GET")){
            printError();
        }
        if (!parts[2].equals("HTTP/1.1") && !parts[2].equals("HTTP/1.0")){
            printError();
        }
        if (parts[1].toLowerCase().contains("http://")) {
            if (parts[1].toLowerCase().indexOf("http://") != 0){
                printError();
            }

        }
    }

    public static String[] parse(String cliReq){
        checkValidity(cliReq);
        String[] parts=cliReq.split(" ");
        String hostName = null;
        String sDomain = null;
        int subDomaonIndex = parts[1].indexOf("/", 7) ;
        if (subDomaonIndex==-1){printError();}
        hostName = parts[1].substring(0, subDomaonIndex + 1);
        try {
            sDomain = parts[1].substring(subDomaonIndex+1, parts[1].length() );
        }catch (Exception e){}
        sDomain = sDomain==null?"":sDomain;
        String[] s = {hostName,sDomain};
        return s;
    }


    private static void printError() {
        throw new RuntimeException("Internal Error");
    }

    public static String returnDate(String message){
        int i = message.indexOf("Date:");
        int j = message.indexOf("\n", i);
        String date = message.substring(i,j+1);
        return date;
    }


    public ClientProccess(Socket serviceSocket) {
        this.serviceSocket = serviceSocket;
    }

    @Override
    public void run() {
        try {
            Scanner cliRequestScanner = new Scanner(serviceSocket.getInputStream());
            PrintStream proxyToClient = new PrintStream(serviceSocket.getOutputStream());


            String cliReq = cliRequestScanner.nextLine();
            String[] hostId = parse(cliReq);
            String remoteResponse = null;
            String hostAddress = hostId[0] + hostId[1];
            System.out.println(hostAddress + " requestd By : " + serviceSocket.getRemoteSocketAddress());
            if (!Server.getCache().containsKey(hostAddress)) {
                do {
                    remoteResponse = sendToRemote(hostId[0], hostId[1], false, null);
                }while (remoteResponse.length()<16);
                if (Integer.parseInt(remoteResponse.substring(0,15).split(" ")[1])==200) {
                    Server.getCache().put(hostAddress, remoteResponse);
                    Server.scheduledExecutorService.schedule(new Updateor(hostId[0] , hostId[1]) , 2 , TimeUnit.MINUTES );
                    Server.saveCache();
                    System.out.println("New Address Cached : " + hostAddress);
                    System.out.println("Cache Size : " + Server.getCache().size());
                }

            } else {
                remoteResponse = Server.getCache().get(hostAddress);
                System.out.println("Exploited From Cache : " + hostAddress);
            }

            proxyToClient.print(remoteResponse);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}



class Updateor implements Runnable{
    String host , sDom  ;

    public Updateor(String host, String sDom) {
        this.host = host;
        this.sDom = sDom;
    }

    @Override
    public void run() {
        try {
            Server.getCache().put(host+sDom, ClientProccess.sendToRemote(host, sDom, true , Server.getCache().get(host+sDom)));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}