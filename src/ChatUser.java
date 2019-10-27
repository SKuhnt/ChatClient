import java.net.InetAddress;

public class ChatUser {
    private String userName;
    private InetAddress inetAddress;

    public ChatUser(String userName, InetAddress inetAddress){
        this.userName = userName;
        this.inetAddress = inetAddress;
    }

    public String getUserName() {
        return userName;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }
}
