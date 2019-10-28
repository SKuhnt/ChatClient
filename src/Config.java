import java.net.InetAddress;
import java.net.UnknownHostException;

public class Config {
    public static final InetAddress LOCALHOST;
    static {
        InetAddress localHost;
        try {
            localHost = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            localHost = null;
            e.printStackTrace();
        }
        LOCALHOST = localHost;
    }

    public static final int UDP_CLIENT_PORT = 53771;
    public static final InetAddress SERVER_ADDRESS = LOCALHOST;
    public static final int UDP_SERVER_PORT = 9876;
    public static final int UDP_BUFFER_SIZE = 1024;
    public static final String UDP_SPLIT_OPERATOR = "\r\n";
    public static final int TCP_SERVER_PORT = 56789;
    public static final String SHOW_ALL_USERS_COMMAND = "getUsers!";



}
