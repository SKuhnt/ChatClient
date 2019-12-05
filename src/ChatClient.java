import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatClient {

    public static void main(String[] args) throws UnknownHostException {
        /* Test: Erzeuge Client und starte ihn. */
        ChatClient myClient = new ChatClient(Config.LOCALHOST, 8192, args[0]);
        myClient.startJob();
    }

    public String userId = "";
    private final String userName;
    private final UDPClient udpClient;
    public List<String> userList;

    private ChatClient(InetAddress inetAddress, int serverPort, String userName) {
        this.userName = userName;
        this.userList = new CopyOnWriteArrayList<>();
        this.udpClient = new UDPClient(this, serverPort, inetAddress);
    }

    private void startJob() {
            /* Client starten. Ende, wenn quit eingegeben wurde */
            //String userId = "";
            Scanner inFromUser;
            String sentence; // vom User uebergebener String
            boolean auth = true;
            boolean serviceRequested = true;
            try {
                while (auth) {
                    udpClient.writeToServer("/c/" + userName + Config.UDP_END_OPERATOR);
                    String answer = udpClient.readFromServer();
                    System.out.println(answer);
                    if (answer.contains("/c/") && answer.contains(Config.UDP_END_OPERATOR)){
                        userId = answer.substring(answer.indexOf("/c/") + 3, answer.indexOf(Config.UDP_END_OPERATOR));
                    }
                    if(!userId.isEmpty()){
                        auth = false;
                    }
                }

                UDPReadThread udpListener = new UDPReadThread(udpClient);
                udpListener.start();

                /* Konsolenstream (Standardeingabe) initialisieren */
                inFromUser = new Scanner(System.in);
                while (serviceRequested) {
                    /* String vom Benutzer (Konsoleneingabe) holen */
                    sentence = inFromUser.nextLine();

                    /* Test, ob Client beendet werden soll */
                    if (sentence.equalsIgnoreCase(Commands.QUIT.name())) {
                        udpClient.writeToServer("/d/" + userId + Config.UDP_END_OPERATOR);
                        serviceRequested = false;
                    } else if(sentence.equalsIgnoreCase(Config.SHOW_ALL_USERS_COMMAND)){
                        System.out.println("Users:\n");
                        System.out.println(String.join("\n", userList));
                    }
                    else {
                        /* Sende den String als UDP-Paket zum Server */
                        udpClient.writeToServer("/m/" + userName + ": " + sentence + Config.UDP_END_OPERATOR);
                    }
                }

                /* Socket-Streams schliessen --> Verbindungsabbau */
                inFromUser.close();
                udpListener.shutDown();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Connection aborted by server!");
            }
            System.out.println("TCP Client stopped!");
        }
}

class UDPClient {
    private static final int BUFFER_SIZE = Config.UDP_BUFFER_SIZE;
    private DatagramSocket clientSocket; // UDP-Socketklasse
    public ChatClient chatClient;

    UDPClient(ChatClient chatClient, int port, InetAddress inetAddress){
        try {
            this.chatClient = chatClient;
            clientSocket = new DatagramSocket(port, inetAddress);
        } catch (Exception ex){
            ex.printStackTrace();
            //todo handle exception
        }
    }

    void writeToServer(String sendString) throws UnknownHostException {
        /* Sende den String als UDP-Paket zu allen Clients */
        /* String in Byte-Array umwandeln */
        byte[] sendData = sendString.getBytes(Config.CHARSET);
            /* Paket erzeugen */
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("LAB26"), 8192);
        /* Senden des Pakets */
        try {
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String readFromServer() throws IOException {
        /* Liefere den naechsten String vom Server */
        /* Paket fuer den Empfang erzeugen */
        byte[] receiveData = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, BUFFER_SIZE);

        /* Warte auf Empfang des Antwort-Pakets auf dem eigenen Port */
        clientSocket.receive(receivePacket);

        /* Paket wurde empfangen --> auspacken und Inhalt anzeigen */
        String receiveString = new String(receivePacket.getData(), Config.CHARSET);
        return receiveString;
    }

    public void closeConnection() {
        clientSocket.close();
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
                String answer = this.UDPCLIENT.readFromServer();
                if(answer.startsWith("/i/")){
                    this.UDPCLIENT.writeToServer("/i/" + this.UDPCLIENT.chatClient.userId + Config.UDP_END_OPERATOR);
                } else if(answer.startsWith("/u/")) {
                    String message = answer.substring(answer.indexOf("/u/") + 3, answer.indexOf(Config.UDP_END_OPERATOR));
                    String[] userNames = message.split("/n/");
                    UDPCLIENT.chatClient.userList = new CopyOnWriteArrayList<>(Arrays.asList(userNames));
                } else if(answer.startsWith("/m/")){
                    String message = answer.substring(answer.indexOf("/m/") + 3, answer.indexOf(Config.UDP_END_OPERATOR));
                    System.out.println(message);
                }
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
