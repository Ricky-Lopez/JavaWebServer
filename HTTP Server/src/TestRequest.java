
public class TestRequest {
	public static void main(String[] args) {
		HTTPRequest r = new HTTPRequest("GET /hello.htm HTTP/1.0\r\n" + 
				"User-Agent: Mozilla/4.0 (compatible; MSIE5.01; Windows NT)\r\n" + 
				"Host: www.tutorialspoint.com\r\n" + 
				"Accept-Language: en-us\r\n" + 
				"Accept-Encoding: gzip, deflate\r\n" + 
				"Connection: Keep-Alive");
		System.out.println(r.toString());
		System.out.println(r.getCommand());
		System.out.println(r.getUri());
		System.out.println(r.getProtocol());
		System.out.println(r.getProtocolVersionNumber());
		
		HTTPRequest r2 = new HTTPRequest(r.getCommand(), r.getUri(), r.getProtocol());
		System.out.println(r2.toString());
	}
}
