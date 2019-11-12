import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {
        /* Test: Erzeuge Client und starte ihn. */
        ChatClient myClient = new ChatClient(String.valueOf(Config.SERVER_ADDRESS.getHostAddress()), Config.TCP_SERVER_PORT, args[0]);
        myClient.startJob();
    }
    public String udpListenPort;
    public String userId = "";
    private final String userName;
    private final TCPClient tcpClient;
    private final UDPClient udpClient;
    public Map<Long,ChatUser> userMap;

    public ChatClient(String hostname, int serverPort, String userName) {
        this.userName = userName;
        this.tcpClient = new TCPClient(hostname, serverPort, this);
        this.userMap = new HashMap<>();
        this.udpClient = new UDPClient(hostname,this);
    }

    public void addUsersToUserMap(String usersString){
        String[] userLines = usersString.split(Config.UDP_SPLIT_OPERATOR);
        Arrays.stream(userLines).forEach(userLine -> {
            String[] userData = userLine.split(Config.INLINE_SEPERATOR);
            if(userData.length==4){
                try {
                    userMap.put(Long.parseLong(userData[0]),new ChatUser(userData[1],InetAddress.getByName(userData[2]),userData[3]));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void startJob() {
        /* Client starten. Ende, wenn quit eingegeben wurde */
        //String userId = "";
        Scanner inFromUser;
        String sentence; // vom User uebergebener String
        boolean auth = true;
        boolean serviceRequested = true;

        try {
            ReadThread readThread = new ReadThread(udpClient);
            readThread.start();
            TCPReadThread tcpListener = new TCPReadThread(tcpClient);
            while (auth) {
                tcpClient.writeToServer(userName+Config.INLINE_SEPERATOR+udpListenPort);
                tcpClient.readFromServer();

                if(!userId.isEmpty()){
                    auth = false;
                }
            }
            tcpListener.start();



            /* Konsolenstream (Standardeingabe) initialisieren */
            inFromUser = new Scanner(System.in);
            while (serviceRequested) {
                /* String vom Benutzer (Konsoleneingabe) holen */
                sentence = inFromUser.nextLine();

                /* Test, ob Client beendet werden soll */
                if (sentence.startsWith("quit")) {
                    serviceRequested = false;
                } else if(sentence.startsWith("lg")){
                    System.out.println("Users:\n");
                    this.userMap.entrySet().stream().forEach(kv-> System.out.println(kv.getValue().getUserName()+"@"+kv.getKey()));
                }
                else {

                    /* Sende den String als UDP-Paket zum Server */
                    udpClient.writeToServer(userId + Config.UDP_SPLIT_OPERATOR + sentence);
                }
            }

            /* Socket-Streams schliessen --> Verbindungsabbau */
            tcpListener.shutDown();
            readThread.shutDown();
            tcpClient.closeConnection();
        } catch (IOException e) {
            System.err.println("Connection aborted by server!");
        }
        System.out.println("TCP Client stopped!");
    }
}

class UDPClient {
    public final int SERVER_PORT = Config.UDP_SERVER_PORT;
    public static final int BUFFER_SIZE = Config.UDP_BUFFER_SIZE;
    private DatagramSocket clientSocket; // UDP-Socketklasse
    private InetAddress serverIpAddress; // IP-Adresse des Zielservers
    private Map<Long,ChatUser> userMap;


    UDPClient(String hostname,ChatClient parent){
        try {
            clientSocket = new DatagramSocket();
            serverIpAddress = InetAddress.getByName(hostname); // Zieladresse
            this.userMap=parent.userMap;
            parent.udpListenPort=String.valueOf(clientSocket.getLocalPort());
        } catch (Exception ex){
            //todo handle exception
        }
    }

    protected void writeToServer(String sendString) throws IOException {
        /* Sende den String als UDP-Paket zum Server */

        /* String in Byte-Array umwandeln */
        byte[] sendData = sendString.getBytes(Config.CHARSET);


        userMap.entrySet().stream().forEach(kv->{
            /* Paket erzeugen */
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                    kv.getValue().getInetAddress(), Integer.parseInt(kv.getValue().getPort()));
            /* Senden des Pakets */
            try {
                clientSocket.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    protected String readFromServer() throws IOException {
        /* Liefere den naechsten String vom Server */
        String receiveString = "";

        /* Paket fuer den Empfang erzeugen */
        byte[] receiveData = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, BUFFER_SIZE);

        /* Warte auf Empfang des Antwort-Pakets auf dem eigenen Port */
        clientSocket.receive(receivePacket);

        /* Paket wurde empfangen --> auspacken und Inhalt anzeigen */
        receiveString = new String(receivePacket.getData(), Config.CHARSET);
        String[] headerAndPayload = receiveString.split(Config.UDP_SPLIT_OPERATOR);
        ChatUser user = null;
        if(headerAndPayload.length>1 && (user=userMap.get(Long.parseLong(headerAndPayload[0])))!=null){
            System.out.println(user.getUserName()+": "+headerAndPayload[1]);
        }
        else{
            throw new IOException("could not find chatuser in string: "+receiveString);
        }

        return receiveString;
    }
}

class TCPClient extends Thread{
    private Socket clientSocket; // TCP-Standard-Socketklasse
    private DataOutputStream outToServer; // Ausgabestream zum Server
    private BufferedReader inFromServer; // Eingabestream vom Server
    private ChatClient parent;

    public TCPClient(String hostname, int serverPort, ChatClient parent) {
        try {
            this.parent=parent;
            /* Socket erzeugen --> Verbindungsaufbau mit dem Server */
            clientSocket = new Socket(hostname, serverPort);

            /* Socket-Basisstreams durch spezielle Streams filtern */
            outToServer = new DataOutputStream(clientSocket.getOutputStream());
            inFromServer = new BufferedReader(new InputStreamReader(
                    clientSocket.getInputStream(),Config.CHARSET));

            /* Socket-Streams schliessen --> Verbindungsabbau */
        } catch (IOException e) {
            System.err.println("Connection aborted by server!");
        }

    }

    protected void writeToServer(String request) throws IOException {
        /* Sende eine Zeile (mit CRLF) zum Server */
        outToServer.write((request + '\r' + '\n').getBytes(Config.CHARSET));
    }

    private void readInitialSetup(BufferedReader in) throws IOException {
        String thisUser = in.readLine();
        parent.userId=thisUser.split(Config.INLINE_SEPERATOR)[0];

        //fill the user table
        String next = null;
        while(!(next = inFromServer.readLine()).isEmpty()){
            String[] user = next.split(Config.INLINE_SEPERATOR);
            if(user.length==4){
                parent.userMap.put(Long.parseLong(user[0]),new ChatUser(user[1],InetAddress.getByName(user[2]),user[3]));
            }
            else{
                throw new IOException("Line is not a user: "+next);
            }
        }
    }

    private void readNewConnection(BufferedReader in) throws IOException {
        String user = in.readLine();
        String[] userAry = user.split(Config.INLINE_SEPERATOR);
        if(userAry.length==4){
            parent.userMap.put(Long.parseLong(userAry[0]),new ChatUser(userAry[1],InetAddress.getByName(userAry[2]),userAry[3]));
        }
        else{
            throw new IOException("Line is not a user: "+user);
        }
    }

    private void deleteConnection(BufferedReader in) throws IOException {
        String user = in.readLine();
        parent.userMap.remove(Long.parseLong(user));
//        String[] userAry = user.split(Config.INLINE_SEPERATOR);
//        if(userAry.length==1){
//            parent.userMap.remove(Long.parseLong(userAry[0]));
//        }
//        else{
//            throw new IOException("Line is not a user: "+user);
//        }
    }

    protected void readFromServer() throws IOException {
        /* Lies die Antwort (reply) vom Server */
        String next = null;
        while(!(next = inFromServer.readLine()).isEmpty()){
            String[] line = next.split(Config.INLINE_SEPERATOR);
            if(line.length>1&&line[0].equals(Config.HEADLINE_START)){
                if(line[1].equals(Commands.FULLTABLE.name())){
                    readInitialSetup(inFromServer);
                }
                else if(line[1].equals(Commands.DISCONNECTED.name())){
                    deleteConnection(inFromServer);
                }
                else if(line[1].equals(Commands.CONNECTED.name())){
                    readNewConnection(inFromServer);
                }
                else{
                    //command not found
                    throw new IOException("Command not found: "+next);
                }
            }
            else{
                //wrong headline start... stay silent?
                throw new IOException("Incorrect headline start: "+next);
            }

        }
    }

    protected void closeConnection() throws IOException{
        clientSocket.close();
    }
}
class ReadThread extends Thread{

    private final UDPClient UDPCLIENT;
    private boolean running;
    protected ReadThread(UDPClient udpClient){
        this.UDPCLIENT = udpClient;
        running=true;
    }

    public void shutDown(){
        running=false;
    }

    @Override
    public void run() {
        try {
            while (running){
                this.UDPCLIENT.readFromServer();
            }
        } catch (Exception ex){
            ex.printStackTrace();
            //todo handle exception
        }
    }
}

class TCPReadThread extends Thread {

    private final TCPClient TCPCLIENT;
    private boolean running;

    protected TCPReadThread(TCPClient tcpClient) {
        this.TCPCLIENT = tcpClient;
        running=true;
    }

    public void shutDown(){
        running=false;
    }

    @Override
    public void run() {
        try {
            while (running) {
                this.TCPCLIENT.readFromServer();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            //return;
            //todo handle exception
        }
    }
}