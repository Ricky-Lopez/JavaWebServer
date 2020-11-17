import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.Socket;

import org.junit.jupiter.api.Test;

class WorkerThreadTest {

	@Test
	void testGetMIMEType() {
		WorkerThread t = new WorkerThread(new Socket());
		System.out.println(t.getMIMEType("/index.html"));
		System.out.println(1.0f);
		System.out.println(1.1f);
	}
	
	/*@Test
	
	void testQueryDecode() {
		WorkerThread t = new WorkerThread(new Socket());
		String result = t.decodeQuery("Exp=1!+2&Res=3");
		System.out.println(result);
		String result2 = t.decodeQuery("x=!!&y=!@");
		System.out.println(result2);
		assertEquals("Exp=1+2&Res=3", result);
	}
	*/
	
	@Test 
	void testNewRequest() {
		WorkerThread t = new WorkerThread(new Socket());
		String requestString = "POST /cgi_bin/CgiQuery.cgi HTTP/1.0\n"+
		        "From: me@mycomputer\n"+
		        "User-Agent: telnet\n" +
		        "Content-Type: application/x-www-form-urlencoded\n"+
		        "Content-Length: 14\n\n" + "If-Modified-Since: Tue, 14 Jul 2015 18:00:00 GMT\r\n" + 
		        		 "Exp=1!+2&Res=3\n";
		t.forTest(requestString);
		
		String s2 = "POST /cgi_bin/env.cig HTTP/1.0\n"+
				"From: me@mycomputer\n"+
				"User-Agent: telnet\n"+
				"Content-Type: application/x-www-form-urlencoded\n"+
				"Content-Length: 14\r\n\r\n"+
				"File=./doc_root/index.html&cost=0\r\n";
		DataOutputStream outToClient = null;
		BufferedReader inFromClient = null;
		t.forTest(s2);
		t.post(new HTTPRequest(s2), outToClient, inFromClient);
		/*
		GET /resources/bitcoin.pdf HTTP/1.0
		If-Modified-Since: foobar 30000
		*/

	}

}
