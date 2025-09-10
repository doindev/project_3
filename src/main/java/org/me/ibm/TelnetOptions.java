package org.me.ibm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TelnetOptions {
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private boolean binaryMode;
    private boolean endOfRecord;
    private String terminalType;
    
    public TelnetOptions(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.binaryMode = false;
        this.endOfRecord = false;
        this.terminalType = TelnetConstants.TERMINAL_TYPE_IBM3278;
    }
    
    public void negotiateOptions() throws IOException {
        // Send initial telnet option negotiations for 3270 terminal
        
        // Negotiate Binary mode
        sendWill(TelnetConstants.BINARY);
        sendDo(TelnetConstants.BINARY);
        
        // Negotiate End of Record
        sendWill(TelnetConstants.END_OF_RECORD);
        sendDo(TelnetConstants.END_OF_RECORD);
        
        // Negotiate Terminal Type
        sendWill(TelnetConstants.TERMINAL_TYPE);
        
        // Suppress Go Ahead
        sendWill(TelnetConstants.SUPPRESS_GO_AHEAD);
        sendDo(TelnetConstants.SUPPRESS_GO_AHEAD);
        
        // Process incoming option negotiations - initial burst
        processInitialNegotiations();
    }
    
    private void processInitialNegotiations() throws IOException {
        // Process telnet negotiations for a reasonable amount of time
        // This allows for the back-and-forth negotiation process
        long startTime = System.currentTimeMillis();
        long timeout = 3000; // 3 seconds for initial negotiations
        
        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                // Read one byte at a time to handle streaming telnet data
                int b = inputStream.read();
                if (b == -1) {
                    break; // End of stream
                }
                
                processTelnetByte((byte) b);
                
            } catch (IOException e) {
                // Socket timeout or other IO error
                break;
            }
        }
    }
    
    private byte[] negotiationBuffer = new byte[256];
    private int bufferPos = 0;
    
    private void processTelnetByte(byte b) throws IOException {
        negotiationBuffer[bufferPos++] = b;
        
        // Check if we have a complete telnet command
        if (bufferPos >= 3 && negotiationBuffer[0] == TelnetConstants.IAC) {
            byte command = negotiationBuffer[1];
            
            switch (command) {
                case TelnetConstants.DO:
                case TelnetConstants.DONT:
                case TelnetConstants.WILL:
                case TelnetConstants.WONT:
                    if (bufferPos >= 3) {
                        processTelnetCommand(negotiationBuffer[1], negotiationBuffer[2]);
                        resetBuffer();
                    }
                    break;
                    
                case TelnetConstants.SB:
                    // Subnegotiation - need to find IAC SE
                    if (bufferPos >= 2 && 
                        negotiationBuffer[bufferPos - 2] == TelnetConstants.IAC && 
                        negotiationBuffer[bufferPos - 1] == TelnetConstants.SE) {
                        processSubnegotiation();
                        resetBuffer();
                    }
                    break;
                    
                default:
                    resetBuffer();
                    break;
            }
        } else if (bufferPos >= negotiationBuffer.length) {
            // Buffer overflow protection
            resetBuffer();
        } else if (bufferPos == 1 && b != TelnetConstants.IAC) {
            // Not a telnet command, reset
            resetBuffer();
        }
    }
    
    private void resetBuffer() {
        bufferPos = 0;
    }
    
    private void processTelnetCommand(byte command, byte option) throws IOException {
        switch (command) {
            case TelnetConstants.DO:
                handleDo(option);
                break;
                
            case TelnetConstants.DONT:
                handleDont(option);
                break;
                
            case TelnetConstants.WILL:
                handleWill(option);
                break;
                
            case TelnetConstants.WONT:
                handleWont(option);
                break;
        }
    }
    
    private void processSubnegotiation() throws IOException {
        if (bufferPos < 5) return; // Too short for valid subnegotiation
        
        byte option = negotiationBuffer[2];
        
        if (option == TelnetConstants.TERMINAL_TYPE && bufferPos >= 5) {
            byte subCommand = negotiationBuffer[3];
            if (subCommand == 1) { // SEND command
                sendTerminalType();
            }
        }
    }
    
    
    private void handleDo(byte option) throws IOException {
        switch (option) {
            case TelnetConstants.BINARY:
                binaryMode = true;
                sendWill(option);
                break;
                
            case TelnetConstants.END_OF_RECORD:
                endOfRecord = true;
                sendWill(option);
                break;
                
            case TelnetConstants.TERMINAL_TYPE:
                sendWill(option);
                break;
                
            case TelnetConstants.SUPPRESS_GO_AHEAD:
                sendWill(option);
                break;
                
            default:
                sendWont(option);
                break;
        }
    }
    
    private void handleDont(byte option) throws IOException {
        sendWont(option);
    }
    
    private void handleWill(byte option) throws IOException {
        switch (option) {
            case TelnetConstants.BINARY:
                binaryMode = true;
                sendDo(option);
                break;
                
            case TelnetConstants.END_OF_RECORD:
                endOfRecord = true;
                sendDo(option);
                break;
                
            case TelnetConstants.SUPPRESS_GO_AHEAD:
                sendDo(option);
                break;
                
            default:
                sendDont(option);
                break;
        }
    }
    
    private void handleWont(byte option) throws IOException {
        switch (option) {
            case TelnetConstants.BINARY:
                binaryMode = false;
                break;
                
            case TelnetConstants.END_OF_RECORD:
                endOfRecord = false;
                break;
        }
        sendDont(option);
    }
    
    
    private void sendTerminalType() throws IOException {
        outputStream.write(TelnetConstants.IAC);
        outputStream.write(TelnetConstants.SB);
        outputStream.write(TelnetConstants.TERMINAL_TYPE);
        outputStream.write(0); // IS command
        outputStream.write(terminalType.getBytes());
        outputStream.write(TelnetConstants.IAC);
        outputStream.write(TelnetConstants.SE);
        outputStream.flush();
    }
    
    private void sendWill(byte option) throws IOException {
        outputStream.write(TelnetConstants.IAC);
        outputStream.write(TelnetConstants.WILL);
        outputStream.write(option);
        outputStream.flush();
    }
    
    private void sendWont(byte option) throws IOException {
        outputStream.write(TelnetConstants.IAC);
        outputStream.write(TelnetConstants.WONT);
        outputStream.write(option);
        outputStream.flush();
    }
    
    private void sendDo(byte option) throws IOException {
        outputStream.write(TelnetConstants.IAC);
        outputStream.write(TelnetConstants.DO);
        outputStream.write(option);
        outputStream.flush();
    }
    
    private void sendDont(byte option) throws IOException {
        outputStream.write(TelnetConstants.IAC);
        outputStream.write(TelnetConstants.DONT);
        outputStream.write(option);
        outputStream.flush();
    }
    
    public boolean isBinaryMode() {
        return binaryMode;
    }
    
    public boolean isEndOfRecord() {
        return endOfRecord;
    }
    
    public String getTerminalType() {
        return terminalType;
    }
    
    public void setTerminalType(String terminalType) {
        this.terminalType = terminalType;
    }
}