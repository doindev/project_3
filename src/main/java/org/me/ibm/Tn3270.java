package org.me.ibm;

import java.io.IOException;
import java.net.Socket;

public class Tn3270 {
    private Socket socket;
    private TelnetOptions telnetOptions;
    private Buffer buffer;
    private Screen screen;
    private DataStreamParser parser;
    private Thread parserThread;
    private boolean connected;
    
    // Default connection parameters
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 23;
    
    public Tn3270() {
        this.connected = false;
        this.buffer = new Buffer();
        this.screen = null; // Will be initialized after connection
        this.parser = null; // Will be initialized after connection
    }
    
    public void connect() throws IOException {
        connect(DEFAULT_HOST, DEFAULT_PORT);
    }
    
    public void connect(String hostname, int port) throws IOException {
        if (connected) {
            throw new IllegalStateException("Already connected. Call disconnect() first.");
        }
        
        try {
            // Create socket connection
            socket = new Socket(hostname, port);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            
            // Initialize telnet options negotiation
            telnetOptions = new TelnetOptions(socket.getInputStream(), socket.getOutputStream());
            telnetOptions.setBuffer(buffer);
            
            // Initialize screen with output stream for sending commands
            screen = new Screen(buffer, socket.getOutputStream());
            
            // Negotiate telnet options and get first non-telnet byte
            final int firstDataByte = telnetOptions.negotiateOptions();
            
            // Initialize and start data stream parser
            parser = new DataStreamParser(buffer, socket.getInputStream(), telnetOptions);
            
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
            
        } catch (IOException e) {
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
    
    public Buffer getBuffer() {
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
    
    public TelnetOptions getTelnetOptions() {
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
    
    // Convenience methods that delegate to screen
    public void sendEnter() throws IOException {
        screen().enter();
    }
    
    public void sendPF(int pfNumber) throws IOException {
        switch (pfNumber) {
            case 1: screen().pf1(); break;
            case 2: screen().pf2(); break;
            case 3: screen().pf3(); break;
            case 4: screen().pf4(); break;
            case 5: screen().pf5(); break;
            case 6: screen().pf6(); break;
            case 7: screen().pf7(); break;
            case 8: screen().pf8(); break;
            case 9: screen().pf9(); break;
            case 10: screen().pf10(); break;
            case 11: screen().pf11(); break;
            case 12: screen().pf12(); break;
            default:
                throw new IllegalArgumentException("Invalid PF key number: " + pfNumber);
        }
    }
    
    public void sendPA(int paNumber) throws IOException {
        switch (paNumber) {
            case 1: screen().pa1(); break;
            case 2: screen().pa2(); break;
            case 3: screen().pa3(); break;
            default:
                throw new IllegalArgumentException("Invalid PA key number: " + paNumber);
        }
    }
    
    public void sendClear() throws IOException {
        screen().clear();
    }
    
    public void type(String text) {
        screen().putString(text);
    }
    
    public void type(int row, int col, String text) {
        BufferPosition pos = new BufferPosition(row, col);
        if (pos.isValid()) {
            screen().putString(pos.getPosition(), text);
        }
    }
    
    public String getScreenText() {
        return screen().getString();
    }
    
    public String getScreenText(String separator) {
        return screen().getString(separator);
    }
    
    public String getScreenText(int row) {
        return screen().getString(row);
    }
    
    public void tab() {
        screen().tab();
    }
    
    public void home() {
        screen().home();
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            if (connected) {
                disconnect();
            }
        } finally {
            super.finalize();
        }
    }
}