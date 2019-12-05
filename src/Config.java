import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Config {
    public static final InetAddress LOCALHOST;
    static {
        InetAddress localHost;
        try {
            localHost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            localHost = null;
            e.printStackTrace();
        }
        LOCALHOST = localHost;
    }

    public static final InetAddress SERVER_ADDRESS = LOCALHOST;
    public static final int UDP_BUFFER_SIZE = 1024;
    public static final String UDP_SPLIT_OPERATOR = "\r\n";
    public static final String TCP_END_OPERATOR = "\r\n";
    public static final String UDP_END_OPERATOR = "/e/";
    public static final String TCP_PROTOCOL_HEADER_SPLIT_OPERATOR = "###";
    public static final String TCP_HEADER_BODY_SPLIT_OPERATOR = "~~~";
    public static final String TCP_BODY_LIST_SPLIT_OPERATOR = ";;;";
    public static final String TCP_BODY_INLINE_SPLIT_OPERATOR = ":::";
    public static final String HEADLINE_START = "myprotocol";
    public static final int TCP_SERVER_PORT = 8192;
    public static final String SHOW_ALL_USERS_COMMAND = "getUsers!";
    public static final Charset CHARSET = StandardCharsets.UTF_8;



}
