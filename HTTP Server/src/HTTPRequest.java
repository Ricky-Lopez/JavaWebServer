
import java.util.StringTokenizer;

public class HTTPRequest {
	private final String IF_MODIFIED = "If-Modified-Since: ";
	private final String HTTP_FROM = "From: ";
	private final String USER_AGENT = "User-Agent: ";
	private final String CONTENT_TYPE = "Content-Type: ";
	private final String CONTENT_LENGTH = "Content-Length: ";
	
	//request line
	private String command;
	private String uri;
	private String protocol;
	private String payload;
	private String from;
	private String userAgent; 
	private String contentType;
	private String contentLength;
	
	
	//header fields
	private String ifModifiedDate;
	
	//parses string to build HTTPRequest object (fields)
	public HTTPRequest(String request) {
		StringTokenizer lines = new StringTokenizer(request, "\n");
		String requestLine = lines.nextToken();
		String[] requestParams = requestLine.split(" "); //I don't believe URIs can have spaces, but if they can, this won't work
		
		switch(requestParams.length) {
		case 0: //missing all parameters
			command = null;
			uri = null;
			protocol = null;
			break;
		case 1: //missing 2 parameters
			command = requestParams[0];
			uri = null;
			protocol = null;
			break;
		case 2: //missing a parameter
			command = requestParams[0];
			uri = requestParams[1];
			protocol = null;
			break;
		default: //message is properly formatted
			command = requestParams[0];
			uri = requestParams[1];
			protocol = requestParams[2];	
			break;
		/*default:
			command = requestParams[0];
			uri = requestParams[1];
			protocol = requestParams[2];
			if(requestParams[3].contains(IF_MODIFIED)) {
				ifModifiedDate = requestParams[4];
				for(int i = 5; i < requestParams.length; i++) {
					ifModifiedDate = ifModifiedDate + " " + requestParams[i];
				}
			}
			break;*/
		}

		while(lines.hasMoreTokens()) {
			String tmp = lines.nextToken();
			if (!tmp.equals("")) {
				if(tmp.length() > 6 && tmp.substring(0,6).equals(HTTP_FROM))
					from = tmp.substring(6);
				else if(tmp.length() > 12 && tmp.substring(0,12).equals(USER_AGENT))
					userAgent = tmp.substring(12);
				else if(tmp.length() > 14 && tmp.substring(0,14).equals(CONTENT_TYPE))
					contentType = tmp.substring(14);
				else if (tmp.length() > 16 && tmp.substring(0, 16).equals(CONTENT_LENGTH))
					contentLength = tmp.substring(16);
				else if (tmp.length() > 19 && tmp.substring(0, 19).equals(IF_MODIFIED))
					ifModifiedDate = tmp.substring(19);
				else
					payload = tmp;
			}	
		}
	}
	
	public HTTPRequest(String command, String uri, String protocol) {
		this.command = command;
		this.uri = uri;
		this.protocol = protocol;
	}
	
	public String toString() {
		String request = "";
		String modified = "";
		String f = "";
		String ua = "";
		String ct = "";
		String cl = "";
		String requestLine = command + " " + uri + " " + protocol;
		request += requestLine;
		if (ifModifiedDate != "" && ifModifiedDate != null) {
			modified = IF_MODIFIED + ifModifiedDate;
			request += "\n" + modified;
		}
		if (from != "" && from != null) {
			f = HTTP_FROM + from;
			request += "\n" + f;
		}
		if (userAgent != "" && userAgent != null) {
			ua = USER_AGENT + userAgent;
			request += "\n" + ua;
		}
		
		if (contentType != "" && contentType != null) {
			ct = CONTENT_TYPE + contentType;
			request += "\n" + ct;
		}
		
		if(contentLength != "" && contentLength != null) {
			cl = CONTENT_LENGTH + contentLength;
			request += "\n" + cl;
		}
		if (payload != "" && payload != null) {
			request += "\n\n" + payload;
		}
		request += "\r\n";
		return request;
	}
	
	public String getCommand() {
		return command;
	}
	
	public String getUri() {
		return uri;
	}
	
	public String getProtocol() {
		return protocol;
	}
	
	public String getProtocolVersionNumber() {
		String[] protocolWithVersion = protocol.split("/");
		if(protocolWithVersion.length == 2) {
			return protocolWithVersion[1];
		}
		else return null;
	}
	public String getIfModifiedBy() {
		return ifModifiedDate;
	}
	
	public String getPayload() {
		return payload;
	}
	
	public String getFrom() {
		return from;
	}
	
	public String getUserAgent() {
		return userAgent;
	}
	
	public String getContentType() {
		return contentType;
	}
	
	//returns null if content length cannot be parsed correctly (non-numeric)
	public Integer getContentLength() {
		if(contentLength == null) {
			return null;
		}
		Integer length = 0;
		try {
			length = Integer.parseInt(contentLength.trim());
		}
		catch(NumberFormatException e) {
			return null;
		}
		return length;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
