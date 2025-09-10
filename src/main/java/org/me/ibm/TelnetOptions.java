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
        
        // Process incoming option negotiations
        processOptionNegotiations();
    }
    
    private void processOptionNegotiations() throws IOException {
        byte[] buffer = new byte[1024];
        int timeoutCounter = 0;
        
        // Process initial option negotiations with timeout
        while (timeoutCounter < 100) { // Adjust timeout as needed
            if (inputStream.available() > 0) {
                int bytesRead = inputStream.read(buffer);
                processTelnetCommands(buffer, bytesRead);
                timeoutCounter = 0; // Reset timeout on activity
            } else {
                try {
                    Thread.sleep(10);
                    timeoutCounter++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    private void processTelnetCommands(byte[] data, int length) throws IOException {
        int i = 0;
        
        while (i < length) {
            if (data[i] == TelnetConstants.IAC && i + 2 < length) {
                byte command = data[i + 1];
                byte option = data[i + 2];
                
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
                        
                    case TelnetConstants.SB:
                        i = handleSubnegotiation(data, i, length);
                        continue;
                }
                
                i += 3;
            } else {
                i++;
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
    
    private int handleSubnegotiation(byte[] data, int index, int length) throws IOException {
        if (index + 3 >= length) return index + 1;
        
        byte option = data[index + 2];
        int i = index + 3;
        
        // Find end of subnegotiation
        while (i < length - 1) {
            if (data[i] == TelnetConstants.IAC && data[i + 1] == TelnetConstants.SE) {
                break;
            }
            i++;
        }
        
        if (option == TelnetConstants.TERMINAL_TYPE && i > index + 3) {
            byte subCommand = data[index + 3];
            if (subCommand == 1) { // SEND command
                sendTerminalType();
            }
        }
        
        return i + 2; // Skip IAC SE
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