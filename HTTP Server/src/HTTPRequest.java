
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.StringTokenizer;

//class models a HTTPRequest
public class HTTPRequest {
	private final String IF_MODIFIED = "If-Modified-Since: ";
	private final String HTTP_FROM = "From: ";
	private final String USER_AGENT = "User-Agent: ";
	private final String CONTENT_TYPE = "Content-Type: ";
	private final String CONTENT_LENGTH = "Content-Length: ";
	private final String COOKIE = "Cookie: ";
	private Boolean hasCookie = false;
	
	//request line
	private String command;
	private String uri;
	private String protocol;
	
	//header fields
	private String payload;
	private String from;
	private String userAgent; 
	private String contentType;
	private String contentLength;
	private String cookie;
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
		}
		
		//read in header lines
		while(lines.hasMoreTokens()) {
			String tmp = lines.nextToken();
			if (!tmp.equals("")) {
				if(tmp.length() > 6 && tmp.substring(0,6).equals(HTTP_FROM)) //Request has "FROM" header
					from = tmp.substring(6);
				else if(tmp.length() > 8 && tmp.substring(0, 8).equals(COOKIE)) { //Check for cookie header
					cookie = tmp.substring(8);
					//Make sure cookie is valid
					if(cookie.substring(0, 9).equals("lasttime=")) {
						String cookieDateString = cookie.substring(9).trim();
						System.out.println("date string" + cookieDateString);
						try {
							cookieDateString = URLDecoder.decode(cookieDateString, "UTF-8");
							System.out.println("decoded cookie date " + cookieDateString);
							LocalDateTime cookie = LocalDateTime.parse(cookieDateString.replace(' ', 'T'));
							LocalDateTime today = LocalDateTime.now();	
							if(cookie.toLocalDate().compareTo(today.toLocalDate()) <= 0) { //cookie date must be in past
								this.hasCookie = true;
							}
						//Date cannot be parsed -> improper formatting/invalid cookie
						} catch (UnsupportedEncodingException | DateTimeParseException ex) { 
							System.out.println(ex.getMessage());
							return;
						}			
					}
				}
				else if(tmp.length() > 12 && tmp.substring(0,12).equals(USER_AGENT)) //request contains USER AGENT header
					userAgent = tmp.substring(12);
				else if(tmp.length() > 14 && tmp.substring(0,14).equals(CONTENT_TYPE)) //request contains CONTENT TYPE header
					contentType = tmp.substring(14);
				else if (tmp.length() > 16 && tmp.substring(0, 16).equals(CONTENT_LENGTH)) //request contains CONTENT LENGTH header
					contentLength = tmp.substring(16);
				else if (tmp.length() > 19 && tmp.substring(0, 19).equals(IF_MODIFIED)) //request contains IF MODIFIED header
					ifModifiedDate = tmp.substring(19);
				else //must be payload/body
					payload += tmp;
			}	
		}

	}
	
	//Creates a new request using command, uri, and protocol
	public HTTPRequest(String command, String uri, String protocol) {
		this.command = command;
		this.uri = uri;
		this.protocol = protocol;
	}
	
	//Returns a string representation of this request
	public String toString() {
		String request = "";
		String modified = "";
		String f = "";
		String ua = "";
		String ct = "";
		String cl = "";
		String cook = "";
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
		if(cookie != "" && cookie != null) {
			cook = COOKIE + cookie;
			request += "\n" + cook;
		}
		if (payload != "" && payload != null) {
			request += "\n\n" + payload;
		}
		request += "\r\n";
		return request;
	}
	
	//returns the request's command, or null if request does not contain a command
	public String getCommand() {
		return command;
	}
	
	//returns the request's URI, or null if request does not contain a URI
	public String getUri() {
		return uri;
	}
	
	//returns the request's protocol, or null if request does not contain a protocol
	public String getProtocol() {
		return protocol;
	}
	
	//returns the requests protocol version, or null if request does not contain a protocol version
	public String getProtocolVersionNumber() {
		String[] protocolWithVersion = protocol.split("/");
		if(protocolWithVersion.length == 2) {
			return protocolWithVersion[1];
		}
		else return null;
	}
	
	//returns the request's ifModified field, or null if request does not contain an if modified field
	public String getIfModifiedBy() {
		return ifModifiedDate;
	}
	
	//returns the request's payload, or null if request does not contain a payload
	public String getPayload() {
		return payload;
	}
	
	//returns the request's from field, or null if request does not contain a from header
	public String getFrom() {
		return from;
	}
	
	//returns the request's user agent field, or null if the request does not contain a user agent header
	public String getUserAgent() {
		return userAgent;
	}
	
	//returns the request's content type field, or null if the request does not contain a content type header
	public String getContentType() {
		return contentType;
	}
	
	//returns the request's cookie field, or null if the request does not contain a cookie
	public String getCookie() {
		return cookie;
	}
	
	//if the request contains a valid cookie, returns true, else returns false
	public Boolean checkCookie() {
		return hasCookie;
	}
	
	//returns the content length field of the request, or null if content length cannot be parsed correctly (non-numeric)
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
