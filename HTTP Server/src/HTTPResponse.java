/** class takes given entities and puts them into properly formatted HTTP response */
public class HTTPResponse {
	private boolean hasHeaderLines;
	private boolean hasCookies;
	
	//Status line entities
	private String protocol;
	private String statusCode;
	private String statusPhrase;
	
	//Header lines --> need for Ok response
	private String allow = "Allow: ";
	private String contentEncoding = "Content-Encoding: ";
	private String contentLength = "Content-Length: "; // only included when entity body is being sent
	private String contentType = "Content-Type: "; //MIME Type
	private String expires = "Expires: ";
	private String lastModified = "Last-Modified: ";
	private String cookie = "Set-Cookie: ";
	
	public HTTPResponse(String protocol, String statusCode, String statusPhrase) {
		this.protocol = protocol;
		this.statusCode = statusCode;
		this.statusPhrase = statusPhrase;
	}
	
	public void addHeaderLines(String allow, String contentLength, String contentType, String expires) {
		this.allow += allow;
		this.contentLength += contentLength;
		this.contentType += contentType;
		this.expires += expires;
		hasHeaderLines = true;
	}
	
	public void addHeaderLines(String allow, String contentEncoding, String contentLength, String contentType, 
			String expires, String lastModified) {
		this.allow += allow;
		this.contentEncoding += contentEncoding;
		this.contentLength += contentLength;
		this.contentType += contentType;
		this.expires += expires;
		this.lastModified += lastModified;
		hasHeaderLines = true;
	}
	
	//If contains a cookie :)
	public void addHeaderLines(String allow, String contentEncoding, String contentLength, String contentType,
			String expires, String lastModified, String cookie) {
		this.allow += allow;
		this.contentEncoding += contentEncoding;
		this.contentLength += contentLength;
		this.contentType += contentType;
		this.expires += expires;
		this.lastModified += lastModified;
		this.cookie += cookie;
		hasHeaderLines = true;
		hasCookies = true;
	}
	
	public String generateHttpResponse() {
		String response = "";
		String statusLine = protocol + " " + statusCode + " " + statusPhrase;
		String headerLines = "";
		if (hasHeaderLines) {
			headerLines += (allow != null) ? allow + "\r\n" : "";
			headerLines += (contentEncoding != null) ? contentEncoding + "\r\n" : "";
			headerLines += (contentLength != null) ? contentLength + "\r\n" : "";
			headerLines += (contentType != null) ? contentType + "\r\n" : "";
			headerLines += (expires != null) ? expires + "\r\n" : "";
			headerLines += (lastModified != null) ? lastModified + "\r\n" : "";
			if(this.hasCookies) {
				headerLines += (cookie != null) ? cookie + "\r\n" : "";
			}
			
			return response += (statusLine + "\r\n" + headerLines + "\r\n");
		}
		return statusLine + "\r\n";
	}
}
