
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class PartialHTTP1Server {

	public static void main(String[] args) {
		final int INITIAL_POOL_SIZE = 5;
		final int MAX_THREAD_COUNT = 50;
		final String PROTOCOL = "HTTP/1.0";
		final String PROTOCOL1 = "HTTP/1.1";
		final String UNAVAILABLE_CODE = "503";
		final String UNAVAILABLE_MSG = "Service Unavailable";
		
		ServerSocket welcomeSocket = null;
		
		//creating welcome socket
		try {
			welcomeSocket = new ServerSocket(Integer.parseInt(args[0]));
			System.out.println("Server successfully started on port " + Integer.parseInt(args[0]));
		}
		catch (NumberFormatException e) {
			System.out.println("Port number must consist of digits!");
			return;
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
			return;
		} 
		catch(IllegalArgumentException e) {
			System.out.println("Port number must be between 0 and 65535!");
			return;
		}
		
		//create "threadpool" to hold worker threads
		ArrayList<WorkerThread> threadPool = new ArrayList<>();
		//create 5 threads and add
		for(int i = 0; i < 5; i++) {
			WorkerThread t = new WorkerThread();
			threadPool.add(t);
		}
		
		//listen for connections
		if(welcomeSocket != null) {
			Socket connectionSocket = null;
			while(true) {
				try {
					connectionSocket = welcomeSocket.accept();
				}
				catch(IOException e) {
					System.out.println(e.getMessage());
				}
				
				//go through thread pool and remove any threads that have finished
				for(int i = 0; i < threadPool.size(); i++) {
					if(threadPool.get(i).getState().equals(Thread.State.valueOf("TERMINATED")) && threadPool.size() > 5) {
						threadPool.remove(i);
					}
				}
				if (connectionSocket != null) {
					//Check if we can hand this connection off to a thread
					if(threadPool.size() < MAX_THREAD_COUNT) { 
						//hand off to worker thread
						boolean assignedThread = false;
						for(int i = 0; i < INITIAL_POOL_SIZE; i++) {
							if(threadPool.get(i).getState().equals(Thread.State.valueOf("NEW"))) { //use this thread
								WorkerThread t = threadPool.get(i);
								t.setSocket(connectionSocket);
								t.start();
								assignedThread = true;
								break;
							}
						}
						if(!assignedThread) { //didn't find available thread -> make new thread
							WorkerThread t = new WorkerThread(connectionSocket);
							threadPool.add(t);
							t.start();	
						}
					}
					else { //We have reached our max of 50 threads, cannot provide service to client
						//return 503 service unavailable -- ONLY TIME WE CAN WRITE FROM MAIN THREAD
						DataOutputStream outToClient = null;
						try {
							outToClient = new DataOutputStream(connectionSocket.getOutputStream());
						} catch (IOException e) {
							e.printStackTrace();
						}
						
						if(outToClient != null) {
							HTTPResponse unavailable = new HTTPResponse(PROTOCOL, UNAVAILABLE_CODE, UNAVAILABLE_MSG);
							try {
								outToClient.writeBytes(unavailable.generateHttpResponse());
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						try {
							outToClient.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
						try {
							outToClient.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						
					}
				}
				
				
			}
		}
	}

}
