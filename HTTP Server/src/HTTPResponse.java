/** class takes given entities and puts them into properly formatted HTTP response */
public class HTTPResponse {
	private boolean hasHeaderLines;
	
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
	
	public HTTPResponse(String protocol, String statusCode, String statusPhrase) {
		this.protocol = protocol;
		this.statusCode = statusCode;
		this.statusPhrase = statusPhrase;
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
	
	public String generateHttpResponse() {
		String response = "";
		String statusLine = protocol + " " + statusCode + " " + statusPhrase;
		String headerLines = "";
		if (hasHeaderLines) {
			headerLines += (allow != null) ? allow + "\r\n" : "";
			headerLines += (contentEncoding != null) ? contentEncoding + "\r\n" : "";
			headerLines += (contentLength != null) ? contentLength + "\r\n" : "";
			headerLines += (contentType != null) ? contentType + "\r\n" : "";
			headerLines += (expires!= null) ? expires + "\r\n" : "";
			headerLines += (lastModified != null) ? lastModified + "\r\n" : "";
			
			return response += (statusLine + "\r\n" + headerLines + "\r\n");
		}
		return statusLine + "\r\n";
	}
}
