import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class ChatServer {

    /* TCP-Server, der Verbindungsanfragen entgegennimmt */
    public static final Map<Long, ChatUser> idChatUserMap = new HashMap<>();

    ChatServer(){
        TCPServer myServer = new TCPServer(Config.TCP_SERVER_PORT, 10);
        myServer.startServer();
    }

    public static void main(String[] args) {
        /* Erzeuge Server und starte ihn */
        ChatServer myServer = new ChatServer();
    }
}

class TCPServer{

    /* Semaphore begrenzt die Anzahl parallel laufender Worker-Threads  */
    public Semaphore workerThreadsSem;

    /* Portnummer */
    public final int serverPort;

    /* Anzeige, ob der Server-Dienst weiterhin benoetigt wird */
    public boolean serviceRequested = true;

    /* Konstruktor mit Parametern: Server-Port, Maximale Anzahl paralleler Worker-Threads*/
    public TCPServer(int serverPort, int maxThreads) {
        this.serverPort = serverPort;
        this.workerThreadsSem = new Semaphore(maxThreads);
        Thread udpServerThread = new UDPServerThread();
        udpServerThread.start();
    }

    public void startServer() {
        ServerSocket welcomeSocket; // TCP-Server-Socketklasse
        Socket connectionSocket; // TCP-Standard-Socketklasse

        int nextThreadNumber = 0;

        try {
            /* Server-Socket erzeugen */
            welcomeSocket = new ServerSocket(serverPort);

            while (serviceRequested) {
                workerThreadsSem.acquire();  // Blockieren, wenn max. Anzahl Worker-Threads erreicht

                System.out.println("TCP Server is waiting for connection - listening TCP port " + serverPort);
                /*
                 * Blockiert auf Verbindungsanfrage warten --> nach Verbindungsaufbau
                 * Standard-Socket erzeugen und an connectionSocket zuweisen
                 */
                connectionSocket = welcomeSocket.accept();


                /* Neuen Arbeits-Thread erzeugen und die Nummer, den Socket sowie das Serverobjekt uebergeben */
                (new TCPWorkerThread(nextThreadNumber++, connectionSocket, this)).start();
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
    private int name;
    private Socket socket;
    private TCPServer server;
    private BufferedReader inFromClient;
    private DataOutputStream outToClient;
    boolean workerServiceRequested = true; // Arbeitsthread beenden?

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
            inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outToClient = new DataOutputStream(socket.getOutputStream());

            while (workerServiceRequested) {
                /* String vom Client empfangen und in Grossbuchstaben umwandeln */
                String name = readFromClient();
                ChatServer.idChatUserMap.put(this.getId(), new ChatUser(name, socket.getInetAddress()));
                /* Modifizierten String an Client senden */
                writeToClient(String.valueOf(currentThread().getId()));

                /* Test, ob Arbeitsthread beendet werden soll */
                workerServiceRequested = false;
            }

            boolean connectionOpen = true;
            while (connectionOpen){
                readFromClient();
                //active waiting? - not needed atm
            }

            /* Socket-Streams schliessen --> Verbindungsabbau */
            socket.close();
        } catch (IOException e) {
            System.err.println("Connection aborted by client!");
        } finally {
            System.out.println("TCP Worker Thread " + name + " stopped!");
            /* Platz fuer neuen Thread freigeben */
            server.workerThreadsSem.release();
            ChatServer.idChatUserMap.remove(this.getId());
        }
    }

    private String readFromClient() throws IOException {
        /* Lies die naechste Anfrage-Zeile (request) vom Client */
        String request = inFromClient.readLine();
        System.out.println("TCP Worker Thread " + name + " detected job: " + request);

        return request;
    }

    private void writeToClient(String reply) throws IOException {
        /* Sende den String als Antwortzeile (mit CRLF) zum Client */
        outToClient.writeBytes(reply + '\r' + '\n');
        System.out.println("TCP Worker Thread " + name +
                " has written the message: " + reply);
    }
}

class UDPServer {
    private final static int SERVER_PORT = Config.UDP_SERVER_PORT;
    private final static int BUFFER_SIZE = Config.UDP_BUFFER_SIZE;
    private final static int USER_PORT = Config.UDP_CLIENT_PORT;
    private final static String SPLIT_OPERATOR = Config.UDP_SPLIT_OPERATOR;

    private InetAddress receivedIPAddress; // IP-Adresse des Clients
    private int receivedPort; // Port auf dem Client
    private DatagramSocket serverSocket; // UDP-Socketklasse
    private boolean serviceRequested = true; // Anzeige, ob der Server-Dienst weiterhin benoetigt wird

    public void startService() {
        try {
            /* UDP-Socket erzeugen (kein Verbindungsaufbau!)
             * Socket wird an den ServerPort gebunden */
            serverSocket = new DatagramSocket(SERVER_PORT);
            System.out.println("UDP Server: Waiting for connection - listening UDP port " + SERVER_PORT);
            while (serviceRequested) {
                String message = readFromClient();

                String reponse = "";
                String[] requests = message.split(SPLIT_OPERATOR);
                if (requests.length == 2){
                    reponse += getChatUser(requests[0]).getUserName() + ": " + requests[1];
                    writeToAllUsers(reponse);
                } else {
                    //toDo this could be improved?
                    //throw new IllegalArgumentException();
                    writeToClient("Something went wrong.");
                }
            }

            /* Socket schließen (freigeben) */
            serverSocket.close();
            System.out.println("Server shut down!");
        } catch (SocketException e) {
            System.err.println("Connection aborted by client!");
        } catch (IOException e) {
            System.err.println("Connection aborted by client!");
        }

        System.out.println("UDP Server stopped!");
    }

    private ChatUser getChatUser(String Id){
        return ChatServer.idChatUserMap.get(Long.valueOf(Id));
    }

    private String readFromClient() throws IOException {
        /* Liefere den nächsten String vom Server */
        String receiveString = "";

        /* Paket für den Empfang erzeugen */
        byte[] receiveData = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, BUFFER_SIZE);

        /* Warte auf Empfang eines Pakets auf dem eigenen Server-Port */
        serverSocket.receive(receivePacket);

        receivedIPAddress = receivePacket.getAddress();
        receivedPort = receivePacket.getPort();

        /* Paket erhalten --> auspacken und analysieren */
        receiveString = new String(receivePacket.getData(), 0, receivePacket.getLength());

        return receiveString;
    }

    private void writeToClient(String sendString) throws IOException {
        /* Sende den String als UDP-Paket zum Client */

        /* String in Byte-Array umwandeln */
        byte[] sendData = sendString.getBytes();

        /* Antwort-Paket erzeugen */
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                receivedIPAddress,
                receivedPort);
        /* Senden des Pakets */
        serverSocket.send(sendPacket);

        System.out.println("UDP Server has sent the message: " + sendString);
    }

    private void writeToAllUsers(String sendString) throws IOException {
        /* String in Byte-Array umwandeln */
        byte[] sendData = sendString.getBytes();
        for(ChatUser chatUser : ChatServer.idChatUserMap.values()){
            /* Antwort-Paket erzeugen */
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                    chatUser.getInetAddress(), USER_PORT);
            /* Senden des Pakets */
            serverSocket.send(sendPacket);
        }

        System.out.println("UDP Server has sent the message: " + sendString);
    }
}

class UDPServerThread extends Thread {

    public void run() {
        UDPServer myServer = new UDPServer();
        myServer.startService();
    }

}