package org.me.ibm;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Tn3270 implements AutoCloseable {
    private Socket socket;
    private int connectTimeout = 10000; // 10 seconds
    private TelnetOptionsNegotiator telnetOptions;
    private Buffer buffer;
    private Screen screen;
    private IDataStreamParser parser;
    private Thread parserThread;
    private boolean connected;
    private boolean secure = true;
    
    // Default connection parameters
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 23;
    
    public Tn3270() {
        this.connected = false;
        this.buffer = new Buffer();
        this.screen = null; // Will be initialized after connection
        this.parser = null; // Will be initialized after connection
    }
    
    public boolean isSecure() {
		return secure;
	}
    
    public void setSecure(boolean secure) {
    	if(connected) {
			throw new IllegalStateException("Cannot change security mode while connected.");
		}
		this.secure = secure;
    }
    
    public void connect() throws IOException {
        connect(DEFAULT_HOST, DEFAULT_PORT);
    }
    
    public void connect(String hostname, int port) throws IOException {
    	if (connected) {
            throw new IllegalStateException("Already connected. Call disconnect() first.");
        }
        
        try {
        	if(secure) {
	        	try {
	        		SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
		            java.net.SocketAddress sockaddr = new java.net.InetSocketAddress(hostname, port);
		            SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket();
		            sslSocket.connect(sockaddr, connectTimeout);
		            sslSocket.startHandshake();
		            socket = sslSocket;
		            secure = true;
	        	} catch (SSLHandshakeException she) {
	        		java.net.SocketAddress sockaddr = new java.net.InetSocketAddress(hostname, port);
					Socket plainSocket = new Socket();
					plainSocket.connect(sockaddr, connectTimeout);
					socket = plainSocket;
					secure = false;
	        	}
        	} else {
        		java.net.SocketAddress sockaddr = new java.net.InetSocketAddress(hostname, port);
				Socket plainSocket = new Socket();
				plainSocket.connect(sockaddr, connectTimeout);
				socket = plainSocket;
        	}
          
        	boolean gotLock = false;
        	try {
        		gotLock = buffer.acquireLock();

        		// Initialize telnet options negotiation
        		telnetOptions = new TelnetOptionsNegotiator(socket.getInputStream(), socket.getOutputStream());
        		telnetOptions.setBuffer(buffer);
            
        		// Initialize screen with output stream for sending commands
        		screen = new Screen(buffer, socket.getOutputStream());
            
        		// Negotiate telnet options and get first non-telnet byte
        		final int firstDataByte = telnetOptions.negotiateOptions();
            
        		// Initialize and start data stream parser
        		parser = new DataStreamParser(buffer, socket.getInputStream());
            
        		parserThread = new Thread(() -> {
        			try {
        				parser.parse(firstDataByte);
        			} catch (IOException e) {
        				if (connected) {
        					System.err.println("Data stream parser error: " + e.getMessage());
        				}
        				// Connection lost, mark as disconnected
        				connected = false;
        			}
        		}, "TN3270-Parser");
            
        		parserThread.setDaemon(true);
        		parserThread.start();
            
        		connected = true;
        	} finally{
        		if(gotLock){
        			buffer.awaitEor();
        			buffer.unlock();
        		}
        	}
        } catch (IOException | InterruptedException | TimeoutException e) {
        	cleanup();
            throw new IOException("Failed to connect to " + hostname + ":" + port, e);
        }
    }
    
    public void disconnect() throws IOException {
        if (!connected) {
            return;
        }
        
        connected = false;
        
        try {
            // Stop the parser
            if (parser != null) {
                parser.stop();
            }
            
            // Wait for parser thread to finish (with timeout)
            if (parserThread != null && parserThread.isAlive()) {
                try {
                    parserThread.join(1000); // Wait up to 1 second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Close socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
        } finally {
            cleanup();
        }
    }
    
    private void cleanup() {
        connected = false;
        socket = null;
        telnetOptions = null;
        screen = null;
        parser = null;
        parserThread = null;
    }
    
    public Screen screen() {
        if (!connected || screen == null) {
            throw new IllegalStateException("Not connected. Call connect() first.");
        }
        return screen;
    }
    
    public Buffer buffer() {
        return buffer;
    }
    
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
    
    public String getConnectionInfo() {
        if (!connected || socket == null) {
            return "Not connected";
        }
        
        return String.format("Connected to %s:%d (Local: %s:%d)",
                socket.getInetAddress().getHostAddress(),
                socket.getPort(),
                socket.getLocalAddress().getHostAddress(),
                socket.getLocalPort());
    }
    
    public TelnetOptionsNegotiator getTelnetOptions() {
        return telnetOptions;
    }
    
    public void addScreenUpdateListener(ScreenUpdateListener listener) {
        buffer.addScreenUpdateListener(listener);
    }
    
    public void removeScreenUpdateListener(ScreenUpdateListener listener) {
        buffer.removeScreenUpdateListener(listener);
    }
    
    public void waitForConnection() throws InterruptedException {
        // Wait for initial connection and option negotiation to complete
        Thread.sleep(500); // Basic wait - could be improved with proper synchronization
    }
    
    public void waitForScreenUpdate() throws InterruptedException {
        // Simple implementation - could be improved with proper synchronization
        Object monitor = new Object();
        ScreenUpdateListener listener = new ScreenUpdateListener() {
            @Override
            public void onScreenUpdate() {
                synchronized (monitor) {
                    monitor.notify();
                }
            }
        };
        
        addScreenUpdateListener(listener);
        try {
            synchronized (monitor) {
                monitor.wait(5000); // Wait up to 5 seconds
            }
        } finally {
            removeScreenUpdateListener(listener);
        }
    }
    
    @Override
    public void close() throws IOException {
        if (connected) {
            disconnect();
        }
    }
}
