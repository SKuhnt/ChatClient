import java.net.InetAddress;

public class ChatUser {
    private final String userName;
    private final InetAddress inetAddress;
    private final String port;

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

    public String createBody(Long id){
        return id + Config.TCP_BODY_INLINE_SPLIT_OPERATOR + getUserName() + Config.TCP_BODY_INLINE_SPLIT_OPERATOR + getInetAddressString() + Config.TCP_BODY_INLINE_SPLIT_OPERATOR + getPort();
    }
}
