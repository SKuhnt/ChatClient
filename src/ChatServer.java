import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ChatServer {

    public static void main(String[] args) {
        /* Erzeuge Server und starte ihn */
        new ChatServer();
    }

    /* TCP-Server, der Verbindungsanfragen entgegennimmt */
    public static final Map<Long, ChatUser> idChatUserMap = new HashMap<>();

    private ChatServer(){
        TCPServer myServer = new TCPServer(Config.TCP_SERVER_PORT, 10);
        myServer.startServer();
    }
}

class TCPServer{

    /* Semaphore begrenzt die Anzahl parallel laufender Worker-Threads  */
    public final Semaphore workerThreadsSem;

    /* Portnummer */
    private final int serverPort;

    public final List<TCPWorkerThread> threadList = new ArrayList<>();

    /* Konstruktor mit Parametern: Server-Port, Maximale Anzahl paralleler Worker-Threads*/
    public TCPServer(int serverPort, int maxThreads) {
        this.serverPort = serverPort;
        this.workerThreadsSem = new Semaphore(maxThreads);
    }

    public void startServer() {
        ServerSocket welcomeSocket; // TCP-Server-Socketklasse
        Socket connectionSocket; // TCP-Standard-Socketklasse

        int nextThreadNumber = 0;

        try {
            /* Server-Socket erzeugen */
            welcomeSocket = new ServerSocket(serverPort);

            /* Anzeige, ob der Server-Dienst weiterhin benoetigt wird */
            boolean serviceRequested = true;
            while (serviceRequested) {
                workerThreadsSem.acquire();  // Blockieren, wenn max. Anzahl Worker-Threads erreicht

                System.out.println("TCP Server is waiting for connection - listening TCP port " + serverPort);
                /*
                 * Blockiert auf Verbindungsanfrage warten --> nach Verbindungsaufbau
                 * Standard-Socket erzeugen und an connectionSocket zuweisen
                 */
                connectionSocket = welcomeSocket.accept();


                /* Neuen Arbeits-Thread erzeugen und die Nummer, den Socket sowie das Serverobjekt uebergeben */
                TCPWorkerThread newWorker = (new TCPWorkerThread(nextThreadNumber++, connectionSocket, this));
                newWorker.start();
                threadList.add(newWorker);
            }
        } catch (Exception e) {
            System.err.println(e.toString());
        }
    }

}

// ----------------------------------------------------------------------------

class TCPWorkerThread extends Thread {
    /*
     * Arbeitsthread, der eine existierende Socket-Verbindung zur Bearbeitung
     * erhaelt
     */
    private final int name;
    private final Socket socket;
    private final TCPServer server;
    private BufferedReader inFromClient;
    private DataOutputStream outToClient;
    private boolean workerServiceRequested = true; // Arbeitsthread beenden?

    public TCPWorkerThread(int num, Socket sock, TCPServer server) {
        /* Konstruktor */
        this.name = num;
        this.socket = sock;
        this.server = server;
    }

    public void run() {
        System.out.println("TCP Worker Thread " + name + " " + this.getId() +
                " is running until QUIT is received!");

        try {
            /* Socket-Basisstreams durch spezielle Streams filtern */
            inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream(),Config.CHARSET));
            outToClient = new DataOutputStream(socket.getOutputStream());

            while (workerServiceRequested) {
                /* String vom Client empfangen und in Grossbuchstaben umwandeln */
                String[] userInfo = readFromClient().split(Config.INLINE_SEPERATOR);
                if(userInfo.length==2){
                    ChatServer.idChatUserMap.put(this.getId(), new ChatUser(userInfo[0], socket.getInetAddress(),userInfo[1]));
                    /* Modifizierten String an Client senden */
                    //writeToClient(String.valueOf(currentThread().getId()));
                    writeToClient(initialSetupString(currentThread().getId()));
                    broadCastToAllUsers(userConnectedString(currentThread().getId()));
                    /* Test, ob Arbeitsthread beendet werden soll */
                    workerServiceRequested = false;
                }
            }

            waitForCommands();

            /* Socket-Streams schliessen --> Verbindungsabbau */
            socket.close();
        } catch (IOException e) {
            System.err.println("Connection aborted by client!");
        } finally {
            System.out.println("TCP Worker Thread " + name + " stopped!");
            /* Platz fuer neuen Thread freigeben */
            ChatServer.idChatUserMap.remove(this.getId());
            server.threadList.remove(this);
            broadCastToAllUsers(userDisconnectedString(this.getId()));
            server.workerThreadsSem.release();
            this.interrupt();
        }
    }

    private List<String> connectedUsersMapToString(){
        return ChatServer.idChatUserMap.entrySet().stream().map(kv -> kv.getValue().createBody(kv.getKey())).collect(Collectors.toList());
    }

    private String userDisconnectedString(Long id){
        String res = Config.HEADLINE_START+Config.INLINE_SEPERATOR+Commands.DISCONNECTED.name()+Config.UDP_SPLIT_OPERATOR;
        //ChatUser user = ChatServer.idChatUserMap.get(id);
        return res+id+Config.UDP_SPLIT_OPERATOR;
    }

    private String userConnectedString(Long id){
        String res = Config.HEADLINE_START+Config.INLINE_SEPERATOR+Commands.CONNECTED.name()+Config.UDP_SPLIT_OPERATOR;
        ChatUser user = ChatServer.idChatUserMap.get(id);
        return res+id+Config.INLINE_SEPERATOR+user.getUserName()+Config.INLINE_SEPERATOR+user.getInetAddressString()+Config.INLINE_SEPERATOR+user.getPort()+Config.UDP_SPLIT_OPERATOR;
    }

    private void broadCastToAllUsers(String msg){
        server.threadList.forEach(s-> {
            try {
                s.writeToClient(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private String initialSetupString(Long id){
        ChatUser user = ChatServer.idChatUserMap.get(id);
        List<String> bodies = connectedUsersMapToString();
        bodies.add(user.createBody(id));
        String[] arrayBodies = new String[bodies.size()];
        arrayBodies = bodies.toArray(arrayBodies);
        return new RequestBuilder(Commands.FULLTABLE, arrayBodies).createRequest();
    }

    private String readFromClient() throws IOException {
        /* Lies die naechste Anfrage-Zeile (request) vom Client */
        String request = inFromClient.readLine();
        System.out.println("TCP Worker Thread " + name + " detected job: " + request);

        return request;
    }

    private void waitForCommands() throws IOException {
        boolean isRunning = true;
        while (isRunning){
            /* Lies die naechste Anfrage-Zeile (request) vom Client */
            String request = inFromClient.readLine();
            System.out.println("TCP Worker Thread " + name + " detected job: " + request);
            RequestBuilder userRequest = new RequestBuilder(request);
            Commands command = userRequest.getCommand();
            if (command == null){
                System.out.println("request error!");
            } else if (command.equals(Commands.QUIT)){
                RequestBuilder sendReply = new RequestBuilder(Commands.QUIT_ACK, null);
                String reply = sendReply.createRequest();
                writeToClient(reply);
                isRunning = false;
            }
        }
    }

    private void writeToClient(String reply) throws IOException {
        /* Sende den String als Antwortzeile (mit CRLF) zum Client */
        outToClient.writeBytes(reply + '\r' + '\n');
        System.out.println("TCP Worker Thread " + name +
                " has written the message: " + reply);
    }
}
