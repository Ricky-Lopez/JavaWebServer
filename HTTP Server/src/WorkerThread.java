import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WorkerThread extends Thread {
	private Socket clientSocket;
	private final Set<String> SUPPORTED_COMMANDS = Set.of("HEAD", "POST", "GET");
	private final Set<String> VALID_UNSUPPORTED_COMMANDS = Set.of("PUT", "DELETE", "LINK", "UNLINK");
	private final Set<String> SUPPORTED_MIME_TYPES = Set.of("text/html", "text/plain", "image/gif", 
								 "image/jpeg", "image/png", "application/octet-stream", 
								 "application/pdf", "application/x-gzip", "application/zip"); 
	private final String UNSUPPORTED_MIME_DEFAULT = "application/octet-stream";
	private final String REQUIRED_PROTOCOL = "HTTP/1.0";
	private final float REQUIRED_PROTOCOL_VERSION = 1.0f;
	private final float REQUIRED_PROTOCOL_VERSION1 = 1.1f;
	private final int NUM_ENVIRONMENT_VARIABLES = 6;

	@Override
	public void run() {
		// TODO Auto-generated method stub
		//set up read in/write out
				BufferedReader inFromClient = null;
				DataOutputStream outToClient = null;
				try {
					inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				try {
					outToClient = new DataOutputStream(clientSocket.getOutputStream());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {
					clientSocket.setSoTimeout(5000);
				} catch (SocketException e2) {
					//send internal error
					HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "500", "Internal Server Error");
					sendResponse(response, outToClient, null);
					closeConnection(inFromClient, outToClient);
					return;
				}
				
				
				//read in client request
				String clientRequestAsString = "";
				String lineToAdd;
				try {
					int currentByteInt = -1;
					while((currentByteInt = inFromClient.read()) != -1 && inFromClient.ready()) {
						clientRequestAsString += (char) currentByteInt;
					}
				} catch (IOException e1) {	//Test Case #20: if the client sends a NULL request.
					//send 408 Request Timeout
					HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "408", "Request Timeout");
					sendResponse(response, outToClient, null);
					closeConnection(inFromClient, outToClient);
					return;
				}
				
				System.out.println("We Read: " + clientRequestAsString);
				
				/*if(clientRequestAsString.contains("If-Modified-Since:")) { //If client request contains the If Modified header field.
					clientRequestAsString = clientRequestAsString.substring(0, clientRequestAsString.indexOf("If-Modified-Since:")) + " " + clientRequestAsString.substring(clientRequestAsString.indexOf("If-Modified-Since:"));
				}*/
				
				//Convert string request to HTTP request for simplicity
				HTTPRequest clientRequest = new HTTPRequest(clientRequestAsString);
		
				//Check that request uses correct protocol
				
				//If your server receives a request that does not have a version number, it is considered malformed and should get a "400 Bad Request" response.
				float protocolVersion;
				try {
					protocolVersion = Float.parseFloat(clientRequest.getProtocolVersionNumber());
				}
				catch(NullPointerException | NumberFormatException e) { //protocol v. is null or does not contain parseable float
					//send 400 Bad Request
					HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "400", "Bad Request"); //probably separate the sending of each code response into methods to avoid duplicate code
					sendResponse(response, outToClient, null);
					closeConnection(inFromClient, outToClient);
					return;
				}
				
				//  If your server receives a request that has a version number greater than 1.0, the version is higher than what you can support, and you should respond with a "505 HTTP Version Not Supported"
				/*if ((Float.compare(protocolVersion, REQUIRED_PROTOCOL_VERSION) > 0) ||
						(Float.compare(protocolVersion, REQUIRED_PROTOCOL_VERSION1) > 0)) {
					// send 505 HTTP Version Not Supported
					HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "505", "HTTP Version Not Supported");
					sendResponse(response, outToClient, null);
					closeConnection(inFromClient, outToClient);
					return;
				}*/
					
				
				//check that command is supported
				if (!(SUPPORTED_COMMANDS.contains(clientRequest.getCommand()))){
					if (VALID_UNSUPPORTED_COMMANDS.contains(clientRequest.getCommand())) { //valid but unimplemented
						HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "501", "Not Implemented");
						sendResponse(response, outToClient, null);
						closeConnection(inFromClient, outToClient);
						return;
					}
					else {
						//respond with 400 Bad Request
						HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "400", "Bad Request");
						sendResponse(response, outToClient, null);
						closeConnection(inFromClient, outToClient);
						return;
					}
				}
				
				//check that request is not malformed (contains all required parameters)
				if(clientRequest.getCommand() == null || clientRequest.getUri() == null || clientRequest.getProtocol() == null) {
					//respond with 400 bad request
					HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "400", "Bad Request");
					sendResponse(response, outToClient, null);
					closeConnection(inFromClient, outToClient);
					return;	
				}
				
				
				
				//send to appropriate handler (command)
				switch(clientRequest.getCommand()) {
					case "GET":
						if(clientRequest.getIfModifiedBy() != null) {
							conditionalGet(clientRequest, outToClient, inFromClient);
							break;
						}
						get(clientRequest, outToClient, inFromClient);
						break;
					case "POST":
						post(clientRequest, outToClient, inFromClient, clientSocket); //treat post the same as get
						break;
					case "HEAD":
						head(clientRequest, outToClient, inFromClient);
						break;
				}
	}
	
	public WorkerThread(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}
	
	public WorkerThread() {
		
	}
	
	public void setSocket(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}
	
	public void sendResponse(HTTPResponse response, DataOutputStream outToClient, byte[] body) {
		try {
			System.out.println("SENDING: ");
			System.out.println(response.generateHttpResponse());
			outToClient.write(response.generateHttpResponse().getBytes("UTF-8"));
			outToClient.flush();
			if(body != null) {
				//System.out.println("The payload for this response is: " + new String(body, StandardCharsets.UTF_8));
				outToClient.write(body);
				outToClient.flush();
			}
		} catch (IOException e) { 
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outToClient.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void closeConnection(BufferedReader inFromClient, DataOutputStream outToClient) {
			try {
				outToClient.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			synchronized(this) {
				try {
					wait(250L);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			try {
				inFromClient.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				outToClient.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
	}
	
	public void get(HTTPRequest clientRequest, DataOutputStream outToClient, BufferedReader inFromClient) {
		BufferedReader fileReader = null;
		String fileName = clientRequest.getUri().substring(1); //RICKY: cut off the leading forward slash from the filename, as it would not find file otherwise. 
		File requestedFile = new File(fileName);
		
		if(!(requestedFile.canRead()) && requestedFile.exists()) {
			//403 File Forbidden
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "403", "Forbidden");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		
		//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%	PROJECT PART 3	%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		
		/* COMMENTED OUT FOR PROJECT PART 3 ONLY
		try {
			fileReader = new BufferedReader(new FileReader(requestedFile));
		} catch (FileNotFoundException e) {
			//404 Not Found
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "404", "Not Found");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		*/
		try {
			sleep(1000);
		} catch (InterruptedException e3) {
			// Auto-generated catch block
			e3.printStackTrace();
		}
		String body;
        LocalDateTime myDateObj = LocalDateTime.now();
        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = myDateObj.format(myFormatObj);
        
        try {
        	
			String encodedDateTime = URLEncoder.encode(formattedDate, "UTF-8");
			encodedDateTime = "lasttime="+ encodedDateTime.replace("+", "%20");
			
			if( clientRequest.checkCookie()) {    //if the request contains a cookie
				if(clientRequest.getCookie().contains("lasttime=")) { //if the cookie is indeed "lasttime" . 
	
					String clientCookie = clientRequest.getCookie().substring(9);

			        
			        String decodedDateTime = URLDecoder.decode(clientCookie, "UTF-8");
			        
			        body = createHTMLSeen(decodedDateTime);
			        
			        HTTPResponse response1 = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK") ;
			        response1.addHeaderLines(getSupportedCommandsAsString(), "identity", Long.toString(body.length()), "text/html",
			        		generateExpirationDate(), decodedDateTime.substring(0, 19), encodedDateTime);
			        sendResponse(response1, outToClient, body.getBytes());
			        System.out.println(body + "\n");
			        closeConnection(inFromClient, outToClient);
			        return;
			        	
				}
			}

			body = createHTML();
			HTTPResponse response1 = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
			response1.addHeaderLines(getSupportedCommandsAsString(), "identity", Long.toString(body.length()), "text/html",
					generateExpirationDate(), (URLDecoder.decode(encodedDateTime, "UTF-8")).substring(9, 19) , encodedDateTime);
			sendResponse(response1, outToClient, body.getBytes());
			System.out.println(body + "\n");
			closeConnection(inFromClient, outToClient);
			return;
			
		} catch (UnsupportedEncodingException e2) {
			// Auto-generated catch block
			e2.printStackTrace();
		}
		
        //TODO : potentially fix the last-modified header.
        //TODO : account for multiple cookies.
        
		//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		
		HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
		response.addHeaderLines(getSupportedCommandsAsString(), "identity", Long.toString(requestedFile.length()), getMIMEType(clientRequest.getUri()), 
				generateExpirationDate(), toHttpDateFormat(new Date(requestedFile.lastModified())));
		
		
		
		//read text file to get the file as a String
		if(getMIMEType(clientRequest.getUri()).equals("text/html") || getMIMEType(clientRequest.getUri()).equals("text/plain")) {
			body = "";
			try {
				int c;
				while((c = fileReader.read()) != -1) {
					body += (char)c;
				}
				fileReader.close();
			} catch (IOException e) {
				//any exceptions thrown when looking for and reading the file can be handled with 403.
				response = new HTTPResponse(REQUIRED_PROTOCOL, "403", "Forbidden");
				sendResponse(response, outToClient, null);
				closeConnection(inFromClient, outToClient);
				return;
			}
			sendResponse(response, outToClient, body.getBytes());
		}
		
		//read in file as bytes
		else {
			byte[] bodyb = null;
			try {
				bodyb = Files.readAllBytes(Paths.get(clientRequest.getUri().substring(1)));
			} catch (IOException e) {
				//any exceptions thrown when looking for and reading the file can be handled with 403.
				response = new HTTPResponse(REQUIRED_PROTOCOL, "403", "Forbidden");
				sendResponse(response, outToClient, null);
				closeConnection(inFromClient, outToClient);
				try {
					fileReader.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				return;
			}
			sendResponse(response, outToClient, bodyb);
		}
		closeConnection(inFromClient, outToClient);
	}
	
	public String generateExpirationDate() {
		Calendar expireDate = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		expireDate.add(Calendar.DATE, 1); //sets date to be next day
		return toHttpDateFormat(expireDate.getTime());
	}
	
	private String toHttpDateFormat(Date date) {
		SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
		httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return httpDateFormat.format(date);
	}
	
	public String getMIMEType(String filePath) {
		String[] deliminatedString = filePath.split("\\.");
		String fileExtension = deliminatedString[deliminatedString.length-1]; //extension should be last element
		switch(fileExtension) {
		case "htm":
		case "html":
			return "text/html";
		case "txt":
			return "text/plain";
		case "gif":
			return "image/gif";
		case "jpeg":
		case "jpg":
			return "image/jpeg";
		case "png":
			return "image/png";
		case "pdf":
			return "application/pdf";
		case "gz":
			return "application/x-gzip";
		case "zip":
			return "application/zip";
		case "cgl":
			return "application/octet-stream";
		default:
			return UNSUPPORTED_MIME_DEFAULT;
		}
	}
	
	private String getSupportedCommandsAsString() {
		String commands = "";
		for(String s : SUPPORTED_COMMANDS) {
			commands += s + ", ";
		}
		return commands;
	}
	
	public void conditionalGet(HTTPRequest clientRequest, DataOutputStream outToClient, BufferedReader inFromClient) {
		BufferedReader fileReader = null;
		String fileName = clientRequest.getUri().substring(1); //RICKY: cut off the leading forward slash from the filename, as it would not find file otherwise. 
		File requestedFile = new File(fileName);
		
		if(!(requestedFile.canRead()) && requestedFile.exists()) { //exists, but cannot be read -> Forbidden
			//403 File Forbidden
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "403", "Forbidden");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		try {
			fileReader = new BufferedReader(new FileReader(requestedFile));
		} catch (FileNotFoundException e) {
			//404 Not Found
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "404", "Not Found");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		//Compare the dates to determine what we need to respond with
		Date ifModifiedBy = null;
		try {
			ifModifiedBy = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").parse(clientRequest.getIfModifiedBy());
		} catch (ParseException e2) {
			//invalid date -> response is that of regular get
			get(clientRequest, outToClient, inFromClient);
			try {
				fileReader.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		Date lastModified = null;
		try {
			lastModified = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z").parse(toHttpDateFormat(new Date(requestedFile.lastModified())));
		} catch (ParseException e1) {
			//this date comes directly from the file itself, should parse correctly, but if it doesn't, I guess we can just say internal server error.
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL,"500", "Internal Server Error");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			try {
				fileReader.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		
		
		if(lastModified.compareTo(ifModifiedBy) < 1){
			//not modified
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "304", "Not Modified");
			response.addHeaderLines(null, null, null, null, generateExpirationDate(), null);
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			try {
				fileReader.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		
		HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
		response.addHeaderLines(getSupportedCommandsAsString(), "identity", Long.toString(requestedFile.length()), getMIMEType(clientRequest.getUri()), 
				generateExpirationDate(), toHttpDateFormat(new Date(requestedFile.lastModified())));
		
		//read text file to get the file as a String
		if(getMIMEType(clientRequest.getUri()).equals("text/html") || getMIMEType(clientRequest.getUri()).equals("text/plain")) {
			String body = "";
			try {
				int c;
				while((c = fileReader.read()) != -1) {
					body += (char)c;
				}
				fileReader.close();
			} catch (IOException e) {
				//any exceptions thrown when looking for and reading the file can be handled with 403.
				response = new HTTPResponse(REQUIRED_PROTOCOL, "403", "Forbidden");
				sendResponse(response, outToClient, null);
				closeConnection(inFromClient, outToClient);
				return;
			}
			sendResponse(response, outToClient, body.getBytes());
		}
		
		//read in file as bytes
		else {
			byte[] body = null;
			try {
				body = Files.readAllBytes(Paths.get(clientRequest.getUri().substring(1)));
			} catch (IOException e) {
				//any exceptions thrown when looking for and reading the file can be handled with 403.
				response = new HTTPResponse(REQUIRED_PROTOCOL, "403", "Forbidden");
				sendResponse(response, outToClient, null);
				closeConnection(inFromClient, outToClient);
				try {
					fileReader.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				return;
			}
			sendResponse(response, outToClient, body);
		}
		closeConnection(inFromClient, outToClient);	
	}
	
	public void head(HTTPRequest clientRequest, DataOutputStream outToClient, BufferedReader inFromClient) {
		BufferedReader fileReader = null;
		String fileName = clientRequest.getUri().substring(1); //RICKY: cut off the leading forward slash from the filename, as it would not find file otherwise. 
		File requestedFile = new File(fileName);
		try {
			fileReader = new BufferedReader(new FileReader(requestedFile));
			fileReader.close();
		} catch (IOException e) {
			//404 Not Found
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "404", "Not Found");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
		response.addHeaderLines(getSupportedCommandsAsString(), "identity", Long.toString(requestedFile.length()), getMIMEType(clientRequest.getUri()), 
				generateExpirationDate(), toHttpDateFormat(new Date(requestedFile.lastModified())));
		sendResponse(response, outToClient, null);
		closeConnection(inFromClient, outToClient);
	}
	
	public void post(HTTPRequest clientRequest, DataOutputStream outToClient, BufferedReader inFromClient, Socket clientSocket) {
		//When the POST request doesn't have the "Content-Length" header, or the value is not numeric, your server should return "HTTP/1.0 411 Length Required".
		if(clientRequest.getContentLength() == null) {
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "411", "Length Required");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		
		//When the POST request doesn't have the "Content-Type" header, your server should return "HTTP/1.0 500 Internal Server Error".
		if(clientRequest.getContentType() == null) {
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "500", "Internal Server Error");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		/*
		if(clientRequest.getContentLength() == 0) {
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "204", "No Content");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		*/
		
		BufferedReader fileReader = null;
		String fileName = clientRequest.getUri().substring(1); //RICKY: cut off the leading forward slash from the filename, as it would not find file otherwise. 
		File requestedFile = new File(fileName);
		
		if(!fileName.substring(fileName.length()-6).contains(".cgi")) {
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "405", "Method Not Allowed");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		try {
			fileReader = new BufferedReader(new FileReader(requestedFile));
			fileReader.close();
		} catch (IOException e) {
			//404 Not Found
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "404", "Not Found");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		
		String cgiScript = clientRequest.getUri().substring(1); //cut off leading forward slash
		String encodedQueryString = clientRequest.getPayload();

		String decodedQueryString = decodeQuery(encodedQueryString);
		
		
		
		//run a process in server w/ cgi script
		
		
		String systemCommand = "./" + fileName;
		String cmdArray[] = new String[2];
		cmdArray[0] = systemCommand;
		cmdArray[1] = decodedQueryString;
		
		
		Runtime r = Runtime.getRuntime();
		/*ArrayList<String> environmentVariables = new ArrayList<>();
		environmentVariables.add("CONTENT_LENGTH=" + decodedQueryString.getBytes().length);
		environmentVariables.add("SCRIPT_NAME=" + clientRequest.getUri());
		environmentVariables.add("SERVER_NAME=" + clientSocket.getInetAddress().getHostAddress());
		environmentVariables.add("SERVER_PORT=" + clientSocket.getPort());
		environmentVariables.add(clientRequest.getFrom() != null ? "HTTP_FROM=" + clientRequest.getFrom() : null);
		environmentVariables.add(clientRequest.getUserAgent() != null ? "HTTP_USER_AGENT=" + clientRequest.getUserAgent() : null);
		
		String[] env = environmentVariables.toArray(new String[NUM_ENVIRONMENT_VARIABLES]);
		for(int i = 0; i < env.length; i++) {
			System.out.println(env[i]);
		}
		*/
		try {
			ProcessBuilder pb = new ProcessBuilder(systemCommand);
			Map<String, String> env = pb.environment();
			env.put("CONTENT_LENGTH", String.valueOf(clientRequest.getContentLength()));
			env.put("SCRIPT_NAME", clientRequest.getUri());
			env.put("SERVER_NAME", clientSocket.getInetAddress().getHostAddress());
			env.put("SERVER_PORT", String.valueOf(clientSocket.getPort()));
			if(clientRequest.getFrom() != null)
				env.put("HTTP_FROM", clientRequest.getFrom());
			if(clientRequest.getUserAgent() != null)
				env.put("HTTP_USER_AGENT", clientRequest.getUserAgent());
			
			/*for(String variable : env.keySet()) {
				System.out.println(variable + "=" + env.get(variable));
			}*/
			
			Process p = pb.start();
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			OutputStream stdOutput = p.getOutputStream();
			
			byte[] queryStringB = decodedQueryString.getBytes();
			
			stdOutput.write(queryStringB);
			stdOutput.close();
			p.waitFor();
			
			String output = "";
			String line = "";
			while ((line = stdInput.readLine()) != null)
			      output += line + "\n";
			
			
			if(output.length() == 0) {
				HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "204", "No Content");
				sendResponse(response, outToClient, null);
				closeConnection(inFromClient, outToClient);
				return;
			}
			
			
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
			response.addHeaderLines(getSupportedCommandsAsString(), Long.toString(output.getBytes().length), "text/html", 
					generateExpirationDate());
			sendResponse(response,outToClient, output.getBytes());
			closeConnection(inFromClient, outToClient);
			
			//System.out.println("EXIT VALUE IS: " + p.exitValue());
			return;
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) { // Not allowed to execute the .cgi file
			System.out.println("We got an IO Exception because: " + e.getMessage());
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "403", "Forbidden");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		} catch (InterruptedException i) {
			i.printStackTrace();
		}
		
		
		
		
		
		//pass decoded payload to stdin
		
		/*
		HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
		sendResponse(response, outToClient, null);
		closeConnection(inFromClient, outToClient);
		return;
		*/
	}
	
	public static ArrayList <Byte> readBytes(InputStream inputStream) throws IOException {
		ArrayList <Byte> byteList = new ArrayList<Byte>();
		
		int currentByteInt = -1;
		while ((currentByteInt = inputStream.read()) != -1) {
			byte currentByte = (byte) currentByteInt;
			byteList.add(currentByte);
		}
		return byteList;
	}
	
	public String createHTML() {
		return "<html>\n "
				+ "<body>\n "
				+ "<h1> CS 352 Welcome Page </h1>\n "
				+ "<p>\n "
				+ "Welcome! We have not seen you before.\n "
				+ "<p>\n "
				+ "</body>\n "
				+ "</html>";
	}
	
	public String createHTMLSeen(String datetime) {
		return "<html>\n "
				+ "<body>\n "
				+ "<h1>CS 352 Welcome Page </h1>\n "
				+ "<p>\n "
				+ "Welcome back! Your last visit was at: "+datetime+"\n "
				+ "<p>\n "
				+ "</body>\n "
				+ "</html>";
	}
	
	public String decodeQuery(String encoded) {
		String encodedQueryString = encoded;
		String decodedQueryString = "";
		boolean escapeCharSeen = false;
		for(int i = 0; i < encodedQueryString.length(); i++) {
			if(encodedQueryString.charAt(i) == '!' && !escapeCharSeen)
				escapeCharSeen = true;
			else {
				decodedQueryString += encodedQueryString.charAt(i);
				escapeCharSeen = false;
			}
		}
		return decodedQueryString;
	}
	
	//IGNORE THIS RICKY IT IS ONLY FOR TESTING THAT THE CHANGES TO HTTPREQUEST WORK
	public void forTest(String s) {
		HTTPRequest testRequest = new HTTPRequest(s);
		System.out.println(testRequest.toString());
		System.out.println("ua: " + testRequest.getUserAgent());
	}
}
