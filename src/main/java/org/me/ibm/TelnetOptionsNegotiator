package org.me.ibm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TelnetOptionsNegotiator {
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private boolean binaryMode;
    private boolean endOfRecord;
    private String terminalType;
    private Buffer buffer;
    
    public TelnetOptionsNegotiator(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.binaryMode = false;
        this.endOfRecord = false;
        this.terminalType = TelnetConstants.TERMINAL_TYPE_IBM3279;
        this.buffer = null;
    }
    
    public void setBuffer(Buffer buffer) {
        this.buffer = buffer;
    }
    
    public int negotiateOptions() throws IOException {
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
        
        // Process telnet negotiations byte by byte until we get first non-telnet byte
        return processNegotiationsUntilData();
    }
    
    private byte[] negotiationBuffer = new byte[256];
    private int bufferPos = 0;
    private boolean inTelnetCommand = false;
    
    private int processNegotiationsUntilData() throws IOException {
        long startTime = System.currentTimeMillis();
        long timeout = 5000; // 5 seconds timeout for negotiations
        
        while (System.currentTimeMillis() - startTime < timeout) {
            int b = inputStream.read();
            
            if (b == -1) {
                throw new IOException("Connection closed during telnet negotiation");
            }
            
            // Check if this byte starts a telnet command
            if (((byte)b) == TelnetConstants.IAC && !inTelnetCommand) {
                // Start of telnet command
                inTelnetCommand = true;
                bufferPos = 0;
                negotiationBuffer[bufferPos++] = (byte) b;
                continue;
            }
            
            if (inTelnetCommand) {
                // Continue building telnet command
                if (bufferPos < negotiationBuffer.length) {
                    negotiationBuffer[bufferPos++] = (byte) b;
                }
                
                // Check if we have a complete telnet command
                if (isCompleteTelnetCommand()) {
                    Integer dataByte = processTelnetCommand();
                    inTelnetCommand = false;
                    bufferPos = 0;
                    
                    // If processTelnetCommand returned a data byte (from IAC IAC), return it
                    if (dataByte != null) {
                        return dataByte;
                    }
                    
                    // Otherwise, continue reading for more data
                    continue;
                }
                // If telnet command is not complete, continue reading more bytes
                continue;
            }
            
            // This byte is not part of a telnet sequence, it's actual data
            // Make sure it's not IAC (255) - if it is, it should have been handled above
            if (b == TelnetConstants.IAC) {
                // This shouldn't happen, but if it does, treat as start of telnet command
                inTelnetCommand = true;
                bufferPos = 0;
                negotiationBuffer[bufferPos++] = (byte) b;
                continue;
            }
            
            // This is the first non-telnet byte, return it
            return b;
        }
        
        throw new IOException("Telnet negotiation timeout");
    }
    
    private boolean isCompleteTelnetCommand() {
        if (bufferPos < 2) {
			return false;
		}
        
        byte command = negotiationBuffer[1];
        
        switch (command) {
            case TelnetConstants.DO:
            case TelnetConstants.DONT:
            case TelnetConstants.WILL:
            case TelnetConstants.WONT:
                return bufferPos >= 3;
                
            case TelnetConstants.SB:
                // Subnegotiation - look for IAC SE
                if (bufferPos >= 4) {
                    for (int i = 2; i < bufferPos - 1; i++) {
                        if (negotiationBuffer[i] == TelnetConstants.IAC && 
                            negotiationBuffer[i + 1] == TelnetConstants.SE) {
                            return true;
                        }
                    }
                }
                return false;
                
            case TelnetConstants.IAC:
                // IAC IAC - escaped 255 byte, complete when we have both
                return bufferPos >= 2;
                
            case TelnetConstants.SE:
                // End of record marker - complete with just IAC SE
                return bufferPos >= 2;
                
            default:
                return bufferPos >= 2;
        }
    }
    
    private Integer processTelnetCommand() throws IOException {
        if (bufferPos < 2) {
			return null;
		}
        
        byte command = negotiationBuffer[1];
        
        switch (command) {
            case TelnetConstants.DO:
                if (bufferPos >= 3) {
                    handleDo(negotiationBuffer[2]);
                }
                break;
                
            case TelnetConstants.DONT:
                if (bufferPos >= 3) {
                    handleDont(negotiationBuffer[2]);
                }
                break;
                
            case TelnetConstants.WILL:
                if (bufferPos >= 3) {
                    handleWill(negotiationBuffer[2]);
                }
                break;
                
            case TelnetConstants.WONT:
                if (bufferPos >= 3) {
                    handleWont(negotiationBuffer[2]);
                }
                break;
                
            case TelnetConstants.SB:
                processSubnegotiation();
                break;
                
            case TelnetConstants.SE:
                // End of Record marker - important for 3270
                // This should trigger a screen update
                if (buffer != null) {
                    buffer.notifyScreenUpdate();
                }
                break;
                
            case TelnetConstants.IAC:
                // IAC IAC - this represents a literal 255 byte in the data stream
                // Return it as data
                return 255;
        }
        
        return null; // Command was processed, no data to return
    }
    
    private void processSubnegotiation() throws IOException {
        if (bufferPos < 5) {
			return; // Too short for valid subnegotiation
		}
        
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
    
    // Method for ongoing telnet command processing during normal operation
    // Returns: null = not telnet related, -1 = telnet consumed, or data byte (0-255)
    public Integer processOngoingTelnetByte(byte b) throws IOException {
        // Check if this byte starts or continues a telnet command
        if (b == TelnetConstants.IAC && !inTelnetCommand) {
            // Start of telnet command
            inTelnetCommand = true;
            bufferPos = 0;
            negotiationBuffer[bufferPos++] = b;
            return -1; // This byte was consumed as telnet
        }
        
        if (inTelnetCommand) {
            // Continue building telnet command
            if (bufferPos < negotiationBuffer.length) {
                negotiationBuffer[bufferPos++] = b;
            }
            
            // Check if we have a complete telnet command
            if (isCompleteTelnetCommand()) {
                Integer dataByte = processTelnetCommand();
                inTelnetCommand = false;
                bufferPos = 0;
                
                if (dataByte != null) {
                    // IAC IAC resulted in a literal 255 byte
                    return dataByte;
                } else {
                    // Normal telnet command was processed
                    return -1;
                }
            }
            return -1; // This byte was consumed as telnet (command not complete yet)
        }
        
        return null; // This byte is not telnet-related
    }
}
