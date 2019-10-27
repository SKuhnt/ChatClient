import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {
        /* Test: Erzeuge Client und starte ihn. */
        ChatClient myClient = new ChatClient(String.valueOf(Config.SERVER_ADDRESS.getHostAddress()), Config.TCP_SERVER_PORT, "Simon");
        myClient.startJob();
    }

    private final String userName;
    private final TCPClient tcpClient;
    private final UDPClient udpClient;

    public ChatClient(String hostname, int serverPort, String userName) {
        this.userName = userName;
        this.tcpClient = new TCPClient(hostname, serverPort);
        this.udpClient = new UDPClient(hostname);
    }

    public void startJob() {
        /* Client starten. Ende, wenn quit eingegeben wurde */
        String userId = "";
        Scanner inFromUser;
        String sentence; // vom User uebergebener String
        boolean auth = true;
        boolean serviceRequested = true;

        try {
            while (auth) {
                tcpClient.writeToServer(userName);
                userId = tcpClient.readFromServer();
                if(!userId.isBlank() && !userId.isEmpty()){
                    auth = false;
                }
            }
            ReadThread readThread = new ReadThread(udpClient);
            readThread.start();
            /* Konsolenstream (Standardeingabe) initialisieren */
            inFromUser = new Scanner(System.in);
            while (serviceRequested) {
                /* String vom Benutzer (Konsoleneingabe) holen */
                sentence = inFromUser.nextLine();

                /* Test, ob Client beendet werden soll */
                if (sentence.startsWith("quit")) {
                    serviceRequested = false;
                } else {

                    /* Sende den String als UDP-Paket zum Server */
                    udpClient.writeToServer(userId + Config.UDP_SPLIT_OPERATOR + sentence);

                    /* Modifizierten String vom Server empfangen */
                    udpClient.readFromServer();
                }
            }

            /* Socket-Streams schliessen --> Verbindungsabbau */
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

    UDPClient(String hostname){
        System.out.println(hostname);
        try {
            clientSocket = new DatagramSocket(Config.UDP_CLIENT_PORT);
            serverIpAddress = InetAddress.getByName(hostname); // Zieladresse
        } catch (Exception ex){

        }
    }

    protected void writeToServer(String sendString) throws IOException {
        /* Sende den String als UDP-Paket zum Server */

        /* String in Byte-Array umwandeln */
        byte[] sendData = sendString.getBytes();

        /* Paket erzeugen */
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                serverIpAddress, SERVER_PORT);
        /* Senden des Pakets */
        clientSocket.send(sendPacket);
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
        receiveString = new String(receivePacket.getData(), 0,
                receivePacket.getLength());
        System.out.println(receiveString);
        return receiveString;
    }
}

class TCPClient{
    private Socket clientSocket; // TCP-Standard-Socketklasse
    private DataOutputStream outToServer; // Ausgabestream zum Server
    private BufferedReader inFromServer; // Eingabestream vom Server

    public TCPClient(String hostname, int serverPort) {
        try {
            /* Socket erzeugen --> Verbindungsaufbau mit dem Server */
            clientSocket = new Socket(hostname, serverPort);

            /* Socket-Basisstreams durch spezielle Streams filtern */
            outToServer = new DataOutputStream(clientSocket.getOutputStream());
            inFromServer = new BufferedReader(new InputStreamReader(
                    clientSocket.getInputStream()));

            /* Socket-Streams schliessen --> Verbindungsabbau */
        } catch (IOException e) {
            System.err.println("Connection aborted by server!");
        }
    }

    protected void writeToServer(String request) throws IOException {
        /* Sende eine Zeile (mit CRLF) zum Server */
        outToServer.writeBytes(request + '\r' + '\n');
    }

    protected String readFromServer() throws IOException {
        /* Lies die Antwort (reply) vom Server */
        String reply = inFromServer.readLine();
        return reply;
    }

    protected void closeConnection() throws IOException{
        clientSocket.close();
    }
}
class ReadThread extends Thread{

    private final UDPClient UDPCLIENT;

    protected ReadThread(UDPClient udpClient){
        this.UDPCLIENT = udpClient;
    }

    @Override
    public void run() {
        try {
            while (true){
                this.UDPCLIENT.readFromServer();
            }
        } catch (Exception ex){
        }
    }
}
