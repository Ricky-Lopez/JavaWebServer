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

	@Override
	public void run() {

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
				} catch (IOException e1) { //If the client sends a NULL request.
					//send 408 Request Timeout
					HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "408", "Request Timeout");
					sendResponse(response, outToClient, null);
					closeConnection(inFromClient, outToClient);
					return;
				}
				
				System.out.println("We Read: " + clientRequestAsString);
				
				//Convert string request to HTTP request for simplicity
				HTTPRequest clientRequest = new HTTPRequest(clientRequestAsString);
				
				//If your server receives a request that does not have a version number, it is considered malformed and should get a "400 Bad Request" response.
				try {
					Float.parseFloat(clientRequest.getProtocolVersionNumber());
				}
				catch(NullPointerException | NumberFormatException e) { //protocol version is null or does not contain parseable float
					//send 400 Bad Request
					HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "400", "Bad Request");
					sendResponse(response, outToClient, null);
					closeConnection(inFromClient, outToClient);
					return;
				}	
				
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
	} //end run method
	
	//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%	CONSTRUCTORS   %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
	//Creates a WorkerThread that uses the given socket for communication
	public WorkerThread(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}
	
	//Default empty constructor for WorkerThread -> uses superclass's (Thread) constructor
	public WorkerThread() {
		
	}
	//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%	END CONSTRUCTORS   %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
	
	
	//Sets the socket for this WorkerThread to use for communication
	public void setSocket(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}
	
	//Sends the given HTTP Response to the client. If request has no body, send null for body parameter
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
	
	//Flushes output streams and closes sockets
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
	
	//Method to fulfill Get requests
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
			
			//if request does not contain a cookie
			body = createHTML();
			HTTPResponse response1 = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
			response1.addHeaderLines(getSupportedCommandsAsString(), "identity", Long.toString(body.length()), "text/html",
					generateExpirationDate(), (URLDecoder.decode(encodedDateTime, "UTF-8")).substring(9, 19) , encodedDateTime);
			sendResponse(response1, outToClient, body.getBytes());
			System.out.println(body + "\n");
			closeConnection(inFromClient, outToClient);
			return;
			
		} catch (UnsupportedEncodingException e2) {
			e2.printStackTrace();
		}
        
		//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		
        /* COMMENTED OUT FOR PROJECT PT3 ONLY
        //Create response to send back to client
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
		*/
	}
	
	//Generates an expiration date for the response
	public String generateExpirationDate() {
		Calendar expireDate = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		expireDate.add(Calendar.DATE, 1); //sets date to be next day
		return toHttpDateFormat(expireDate.getTime());
	}
	
	//Formats a date according the HTTP protocol formatting
	private String toHttpDateFormat(Date date) {
		SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
		httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return httpDateFormat.format(date);
	}
	
	//Determines the MIME type of a given file
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
	
	//Formats the set of supported commands into a string
	private String getSupportedCommandsAsString() {
		String commands = "";
		for(String s : SUPPORTED_COMMANDS) {
			commands += s + ", ";
		}
		return commands;
	}
	
	//Fulfills a conditional Get command (has an if-modified-by header)
	public void conditionalGet(HTTPRequest clientRequest, DataOutputStream outToClient, BufferedReader inFromClient) {
		BufferedReader fileReader = null;
		String fileName = clientRequest.getUri().substring(1); //cut off the leading forward slash from the filename, as it would not find file otherwise. 
		File requestedFile = new File(fileName);
		
		if(!(requestedFile.canRead()) && requestedFile.exists()) { //exists, but cannot be read -> Forbidden
			//403 File Forbidden
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "403", "Forbidden");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		
		//Attempt to access the requested file
		try {
			fileReader = new BufferedReader(new FileReader(requestedFile));
		} catch (FileNotFoundException e) { // Send 404 Not Found
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
			//this date comes directly from the file itself, should parse correctly, but if it doesn't, we can just say internal server error.
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL,"500", "Internal Server Error");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			try {
				fileReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}
		
		
		if(lastModified.compareTo(ifModifiedBy) < 1){ //not modified
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "304", "Not Modified");
			response.addHeaderLines(null, null, null, null, generateExpirationDate(), null);
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			try {
				fileReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}
		
		//Send response
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
	
	//Fulfills requests with head command
	public void head(HTTPRequest clientRequest, DataOutputStream outToClient, BufferedReader inFromClient) {
		BufferedReader fileReader = null;
		String fileName = clientRequest.getUri().substring(1); //cut off the leading forward slash from the filename, as it would not find file otherwise. 
		File requestedFile = new File(fileName);
		try {
			fileReader = new BufferedReader(new FileReader(requestedFile));
			fileReader.close();
		} catch (IOException e) { //404 Not Found
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "404", "Not Found");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		
		//create and send response
		HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
		response.addHeaderLines(getSupportedCommandsAsString(), "identity", Long.toString(requestedFile.length()), getMIMEType(clientRequest.getUri()), 
				generateExpirationDate(), toHttpDateFormat(new Date(requestedFile.lastModified())));
		sendResponse(response, outToClient, null);
		closeConnection(inFromClient, outToClient);
	}
	
	//Fullfills requests with post command
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
		
		BufferedReader fileReader = null;
		String fileName = clientRequest.getUri().substring(1); //cut off the leading forward slash from the filename, as it would not find file otherwise. 
		File requestedFile = new File(fileName);
		
		//check that requested file is a cgi script
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
		try {
			//add environment variables
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
			
			//start new process to run cgi script in
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
			
			//Check if cgi script had any output
			if(output.length() == 0) {
				HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "204", "No Content");
				sendResponse(response, outToClient, null);
				closeConnection(inFromClient, outToClient);
				return;
			}
			
			//add cgi output to response
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
			response.addHeaderLines(getSupportedCommandsAsString(), Long.toString(output.getBytes().length), "text/html", 
					generateExpirationDate());
			sendResponse(response,outToClient, output.getBytes());
			closeConnection(inFromClient, outToClient);
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
		
	}
	
	//Creates the HTML string for requests w/o a valid cookie
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
	
	//Creates the HTML string for requests that contain a valid cookie
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
	
	//Decodes pay loads for post requests
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

}
