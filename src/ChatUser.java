import java.net.InetAddress;

public class ChatUser {
    private String userName;
    private InetAddress inetAddress;
    private String port;

    public ChatUser(String userName, InetAddress inetAddress, String port){
        this.userName = userName;
        this.inetAddress = inetAddress;
        this.port=port;
    }

    public String getUserName() {
        return userName;
    }

    public String getPort() {
        return port;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public String getInetAddressString() {
        return inetAddress.getHostAddress();
    }
}
