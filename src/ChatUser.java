import java.net.InetAddress;

public class ChatUser {
    private final String userName;


    public ChatUser(String userName, InetAddress inetAddress, String port){
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

}
