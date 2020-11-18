import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;

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
					System.out.println(clientRequestAsString);
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
				if (Float.compare(protocolVersion, REQUIRED_PROTOCOL_VERSION) > 0) {
					// send 505 HTTP Version Not Supported
					HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "505", "HTTP Version Not Supported");
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
						post(clientRequest, outToClient, inFromClient); //treat post the same as get
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
		System.out.println("In GET");
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
		
		try {
			fileReader = new BufferedReader(new FileReader(requestedFile));
		} catch (FileNotFoundException e) {
			//404 Not Found
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "404", "Not Found");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
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
		System.out.println("In CONDITIONAL GET");
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
	
	public void post(HTTPRequest clientRequest, DataOutputStream outToClient, BufferedReader inFromClient) {
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
		
		if(clientRequest.getContentLength() == 0) {
			HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "204", "No Content");
			sendResponse(response, outToClient, null);
			closeConnection(inFromClient, outToClient);
			return;
		}
		
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
		
		System.out.println("RICKY SAYS: "+ decodedQueryString);
		
		
		//run a process in server w/ cgi script
		
		/*
		String systemCommand = "." + fileName;
		
		Runtime r = Runtime.getRuntime();
		Process p = r.exec(systemCommand);
		*/
		
		//pass decoded payload to stdin
		
		//REMOVE
		HTTPResponse response = new HTTPResponse(REQUIRED_PROTOCOL, "200", "OK");
		sendResponse(response, outToClient, null);
		closeConnection(inFromClient, outToClient);
		return;
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
	}
}
