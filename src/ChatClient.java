import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class ChatClient {

    public static void main(String[] args) {
        /* Test: Erzeuge Client und starte ihn. */
        ChatClient myClient = new ChatClient(String.valueOf(Config.SERVER_ADDRESS.getHostAddress()), Config.TCP_SERVER_PORT, args[0]);
        myClient.startJob();
    }

    public String userId = "";
    private final String userName;
    private final TCPClient tcpClient;
    private final UDPClient udpClient;
    public final Map<Long,ChatUser> userMap;

    private ChatClient(String hostname, int serverPort, String userName) {
        this.userName = userName;
        this.tcpClient = new TCPClient(hostname, serverPort, this);
        this.userMap = new ConcurrentHashMap<>();
        this.udpClient = new UDPClient(this);
    }

    private void startJob() {
        if (tcpClient != null && tcpClient.isConnected()) {

            /* Client starten. Ende, wenn quit eingegeben wurde */
            //String userId = "";
            Scanner inFromUser;
            String sentence; // vom User uebergebener String
            boolean auth = true;
            boolean serviceRequested = true;
            try {
                UDPReadThread udpListener = new UDPReadThread(udpClient);
                udpListener.start();
                TCPReadThread tcpListener = new TCPReadThread(tcpClient);
                while (auth) {
                    RequestBuilder requestBuilder = new RequestBuilder(Commands.AUTH, new String[]{userName + Config.TCP_BODY_INLINE_SPLIT_OPERATOR + udpClient.getUdpListenPort()});
                    tcpClient.writeToServer(requestBuilder.createRequest());
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
                    if (sentence.equalsIgnoreCase(Commands.QUIT.name())) {
                        tcpClient.writeToServer(new RequestBuilder(Commands.QUIT, null).createRequest());
                        serviceRequested = false;
                    } else if(sentence.equalsIgnoreCase(Config.SHOW_ALL_USERS_COMMAND)){
                        System.out.println("Users:\n");
                        this.userMap.forEach((key, value)-> System.out.println(value.getUserName()+"@"+key));
                    }
                    else {
                        /* Sende den String als UDP-Paket zum Server */
                        udpClient.writeToServer(userId + Config.UDP_SPLIT_OPERATOR + sentence);
                    }
                }

                /* Socket-Streams schliessen --> Verbindungsabbau */
                inFromUser.close();
                tcpListener.shutDown();
                udpListener.shutDown();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Connection aborted by server!");
            }
            System.out.println("TCP Client stopped!");
        }
    }
}

class UDPClient {
    private static final int BUFFER_SIZE = Config.UDP_BUFFER_SIZE;
    private DatagramSocket clientSocket; // UDP-Socketklasse
    private final Map<Long,ChatUser> userMap;

    UDPClient(ChatClient parent){
        this.userMap = parent.userMap;
        try {
            clientSocket = new DatagramSocket();
        } catch (Exception ex){
            //todo handle exception
        }
    }

    void writeToServer(String sendString){
        /* Sende den String als UDP-Paket zu allen Clients */
        /* String in Byte-Array umwandeln */
        byte[] sendData = sendString.getBytes(Config.CHARSET);

        userMap.forEach((key, value)->{
            /* Paket erzeugen */
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                    value.getInetAddress(), Integer.parseInt(value.getPort()));
            /* Senden des Pakets */
            try {
                clientSocket.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    void readFromServer() throws IOException {
        /* Liefere den naechsten String vom Server */
        /* Paket fuer den Empfang erzeugen */
        byte[] receiveData = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, BUFFER_SIZE);

        /* Warte auf Empfang des Antwort-Pakets auf dem eigenen Port */
        clientSocket.receive(receivePacket);

        /* Paket wurde empfangen --> auspacken und Inhalt anzeigen */
        String receiveString = new String(receivePacket.getData(), Config.CHARSET);
        String[] headerAndPayload = receiveString.split(Config.UDP_SPLIT_OPERATOR);
        ChatUser user;
        if(headerAndPayload.length > 1 && (user = userMap.get(Long.parseLong(headerAndPayload[0]))) != null){
            System.out.println(user.getUserName()+": "+headerAndPayload[1]);
        }
        else{
            throw new IOException("could not find chatuser in string: "+receiveString);
        }
    }

    String getUdpListenPort(){
        return String.valueOf(clientSocket.getLocalPort());
    }

    public void closeConnection() {
        clientSocket.close();
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

    void writeToServer(String request) throws IOException {
        /* Sende eine Zeile (mit CRLF) zum Server */
        outToServer.write((request).getBytes(Config.CHARSET));
    }

    private void readInitialSetup(String[] bodies) throws IOException {
        for (int i = 0; i < bodies.length; i++){
            String body = bodies[i];
            String[] userStringArray = body.split(Config.TCP_BODY_INLINE_SPLIT_OPERATOR);
            if (i == 0){
                parent.userId = userStringArray[0];
            } else {
                addUser(userStringArray);
            }
        }
    }

    private void readNewConnection(String[] bodies) throws IOException {
        for (String body : bodies) {
            String[] userStringArray = body.split(Config.TCP_BODY_INLINE_SPLIT_OPERATOR);
            addUser(userStringArray);
        }
    }

    private void addUser(String[] userAry) throws IOException {
        if(userAry.length==4){
            parent.userMap.put(Long.parseLong(userAry[0]),new ChatUser(userAry[1],InetAddress.getByName(userAry[2]),userAry[3]));
        }
        else{
            throw new IOException("Line is not a user: " + Arrays.toString(userAry));
        }
    }

    private void deleteConnection(String[] bodies) throws IOException {
        for (String body : bodies) {
            String[] userStringArray = body.split(Config.TCP_BODY_INLINE_SPLIT_OPERATOR);
            String userId = userStringArray[0];
            parent.userMap.remove(Long.parseLong(userId));
        }
    }

    void readFromServer() throws IOException {
        String request = inFromServer.readLine();
        if (!request.isEmpty()){
            RequestBuilder requestBuilder = new RequestBuilder(request);
            Commands command = requestBuilder.getCommand();
            if(command.equals(Commands.FULLTABLE)){
                readInitialSetup(requestBuilder.body);
            }
            else if(command.equals(Commands.DISCONNECTED)){
                deleteConnection(requestBuilder.body);
            }
            else if(command.equals(Commands.CONNECTED)){
                readNewConnection(requestBuilder.body);
            } else {
                System.out.println("Command not found!");
            }
        }
    }

    void closeConnection(){
        try {
            clientSocket.close();
        } catch (IOException ioEx){
            throw new IllegalArgumentException("Unexpected End");
        }
    }

    Boolean isConnected(){
        return clientSocket != null && clientSocket.isConnected();
    }
}

class UDPReadThread extends Thread{

    private final UDPClient UDPCLIENT;
    private boolean running;
    UDPReadThread(UDPClient udpClient){
        this.UDPCLIENT = udpClient;
        running = true;
    }

    public void shutDown(){
        running = false;
        UDPCLIENT.closeConnection();
        this.interrupt();
    }

    @Override
    public void run() {
        try {
            while (running){
                this.UDPCLIENT.readFromServer();
            }
        } catch (SocketException socketEx){
           if (running){
               socketEx.printStackTrace();
               throw new IllegalArgumentException("Unexpected End");
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

    TCPReadThread(TCPClient tcpClient) {
        this.TCPCLIENT = tcpClient;
        running=true;
    }

    public void shutDown(){
        running = false;
        TCPCLIENT.closeConnection();
        this.interrupt();
    }

    @Override
    public void run() {
        try {
            while (running) {
                this.TCPCLIENT.readFromServer();
            }
        } catch (SocketException socketEx){
            if (running){
                socketEx.printStackTrace();
                throw new IllegalArgumentException("Unexpected End");
            }
        }catch (Exception ex) {
            ex.printStackTrace();
            //todo handle exception
        }
    }
}
