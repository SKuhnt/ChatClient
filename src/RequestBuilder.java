import java.util.Arrays;

public class RequestBuilder {

    public Commands command;
    public String[] body;

    public RequestBuilder(String request){
        String[] protocolSplit = request.split(Config.TCP_PROTOCOL_HEADER_SPLIT_OPERATOR);
        if(protocolSplit.length == 2 && protocolSplit[0].equalsIgnoreCase(Config.HEADLINE_START)){
            String[] headerSplit = protocolSplit[1].split(Config.TCP_HEADER_BODY_SPLIT_OPERATOR);
            if (headerSplit[0] != null){
                String header = headerSplit[0];
                command = Commands.valueOf(header);
                if (headerSplit.length == 2){
                    String bodys = headerSplit[1];
                    this.body = bodys.split(Config.TCP_BODY_LIST_SPLIT_OPERATOR);
                }
            }
        }
    }

    public RequestBuilder(Commands command, String[] body){
        this.command = command;
        this.body = body;
    }

    public Commands getCommand() {
        return command;
    }

    public String[] getBody() {
        return body;
    }

    public String createRequest(){
        return Config.HEADLINE_START + Config.TCP_PROTOCOL_HEADER_SPLIT_OPERATOR + command.name() + Config.TCP_HEADER_BODY_SPLIT_OPERATOR + prepareBody() + Config.TCP_END_OPERATOR;
    }

    private String prepareBody(){
        return body == null ? "" : String.join(Config.TCP_BODY_LIST_SPLIT_OPERATOR, body);
    }

}
