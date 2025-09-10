package org.me.ibm;

import java.io.IOException;
import java.io.InputStream;

public class DataStreamParser {
    private final Buffer buffer;
    private final InputStream inputStream;
    private boolean running;
    
    public DataStreamParser(Buffer buffer, InputStream inputStream) {
        this.buffer = buffer;
        this.inputStream = inputStream;
        this.running = false;
    }
    
    public void parse() throws IOException {
        running = true;
        byte[] readBuffer = new byte[8192];
        int bytesRead;
        
        while (running && (bytesRead = inputStream.read(readBuffer)) != -1) {
            processDataStream(readBuffer, bytesRead);
        }
    }
    
    public void stop() {
        running = false;
    }
    
    private void processDataStream(byte[] data, int length) throws IOException {
        int i = 0;
        
        while (i < length) {
            // Check for IAC (Interpret as Command)
            if (data[i] == TelnetConstants.IAC && i + 1 < length) {
                i = processTelnetCommand(data, i, length);
                continue;
            }
            
            // Process 3270 command
            i = processCommand(data, i, length);
        }
    }
    
    private int processTelnetCommand(byte[] data, int index, int length) throws IOException {
        if (index + 1 >= length) return index + 1;
        
        byte command = data[index + 1];
        
        switch (command) {
            case TelnetConstants.SE: // End of Record marker
                buffer.notifyScreenUpdate();
                return index + 2;
                
            case TelnetConstants.SB: // Subnegotiation
                // Skip subnegotiation data until SE
                int i = index + 2;
                while (i < length - 1) {
                    if (data[i] == TelnetConstants.IAC && data[i + 1] == TelnetConstants.SE) {
                        return i + 2;
                    }
                    i++;
                }
                return length;
                
            default:
                return index + 2;
        }
    }
    
    public int processCommand(byte[] data, int index, int length) throws IOException {
        if (index >= length) return index;
        
        byte command = data[index];
        
        switch (command) {
            case TelnetConstants.WRITE:
                return processWrite(data, index + 1, length);
                
            case TelnetConstants.ERASE_WRITE:
                buffer.clear();
                return processWrite(data, index + 1, length);
                
            case TelnetConstants.ERASE_WRITE_ALTERNATE:
                buffer.clear();
                return processWrite(data, index + 1, length);
                
            case TelnetConstants.READ_BUFFER:
            case TelnetConstants.READ_MODIFIED:
            case TelnetConstants.READ_MODIFIED_ALL:
                // These are typically responses, not commands to process
                return index + 1;
                
            default:
                // Treat as data character
                return processCharacter(data, index, length);
        }
    }
    
    private int processWrite(byte[] data, int index, int length) throws IOException {
        if (index >= length) return index;
        
        // Process Write Control Character
        index = processWriteControlCharacter(data, index, length);
        
        // Process orders and data
        while (index < length) {
            byte b = data[index];
            
            // Check for IAC
            if (b == TelnetConstants.IAC) {
                break;
            }
            
            // Check for orders
            switch (b) {
                case TelnetConstants.SF:
                    index = processStartField(data, index, length);
                    break;
                    
                case TelnetConstants.SFE:
                    index = processStartFieldExtended(data, index, length);
                    break;
                    
                case TelnetConstants.SBA:
                    index = processSetBufferAddress(data, index, length);
                    break;
                    
                case TelnetConstants.SA:
                    index = processSetAttribute(data, index, length);
                    break;
                    
                case TelnetConstants.MF:
                    index = processModifyField(data, index, length);
                    break;
                    
                case TelnetConstants.IC:
                    index = processInsertCursor(data, index, length);
                    break;
                    
                case TelnetConstants.PT:
                    index = processProgramTab(data, index, length);
                    break;
                    
                case TelnetConstants.RA:
                    index = processRepeatToAddress(data, index, length);
                    break;
                    
                case TelnetConstants.GE:
                    index = processGraphicsEscape(data, index, length);
                    break;
                    
                default:
                    index = processCharacter(data, index, length);
                    break;
            }
        }
        
        return index;
    }
    
    public int processWriteControlCharacter(byte[] data, int index, int length) {
        if (index >= length) return index;
        
        byte wcc = data[index];
        
        // Process Write Control Character bits
        if ((wcc & TelnetConstants.WCC_RESET) != 0) {
            // Reset operation
        }
        
        if ((wcc & TelnetConstants.WCC_PRINT) != 0) {
            // Print operation
        }
        
        if ((wcc & TelnetConstants.WCC_START_PRINTER) != 0) {
            // Start printer
        }
        
        if ((wcc & TelnetConstants.WCC_SOUND_ALARM) != 0) {
            // Sound alarm
        }
        
        if ((wcc & TelnetConstants.WCC_KEYBOARD_RESTORE) != 0) {
            // Restore keyboard
        }
        
        return index + 1;
    }
    
    public int processStartField(byte[] data, int index, int length) {
        if (index + 1 >= length) return index + 1;
        
        byte attribute = data[index + 1];
        int currentPos = buffer.getCursorPosition();
        
        buffer.setFieldStart(currentPos, true);
        buffer.setAttribute(currentPos, attribute);
        buffer.setCharacter(currentPos, ' '); // Field attribute position is typically blank
        
        // Move cursor to next position
        buffer.setCursorPosition((currentPos + 1) % buffer.getBufferSize());
        
        return index + 2;
    }
    
    public int processStartFieldExtended(byte[] data, int index, int length) {
        if (index + 1 >= length) return index + 1;
        
        int pos = index + 1;
        byte paramCount = data[pos++];
        
        int currentPos = buffer.getCursorPosition();
        buffer.setFieldStart(currentPos, true);
        
        // Process extended attributes
        for (int i = 0; i < paramCount && pos + 1 < length; i++) {
            byte attrType = data[pos++];
            byte attrValue = data[pos++];
            
            if (attrType == (byte) 0xC0) { // Basic attribute
                buffer.setAttribute(currentPos, attrValue);
            }
            // Handle other extended attributes as needed
        }
        
        buffer.setCharacter(currentPos, ' ');
        buffer.setCursorPosition((currentPos + 1) % buffer.getBufferSize());
        
        return pos;
    }
    
    public int processSetBufferAddress(byte[] data, int index, int length) {
        if (index + 2 >= length) return index + 1;
        
        int address = decodeAddress(data[index + 1], data[index + 2]);
        if (address >= 0 && address < buffer.getBufferSize()) {
            buffer.setCursorPosition(address);
        }
        
        return index + 3;
    }
    
    public int processSetAttribute(byte[] data, int index, int length) {
        if (index + 2 >= length) return index + 1;
        
        byte attrType = data[index + 1];
        byte attrValue = data[index + 2];
        
        // Set attribute at current cursor position
        int currentPos = buffer.getCursorPosition();
        if (attrType == (byte) 0xC0) { // Basic attribute
            buffer.setAttribute(currentPos, attrValue);
        }
        
        return index + 3;
    }
    
    public int processModifyField(byte[] data, int index, int length) {
        if (index + 2 >= length) return index + 1;
        
        byte paramCount = data[index + 1];
        int pos = index + 2;
        
        // Process modify field parameters
        for (int i = 0; i < paramCount && pos + 1 < length; i++) {
            byte attrType = data[pos++];
            byte attrValue = data[pos++];
            
            // Modify field attributes at current position
            if (attrType == (byte) 0xC0) {
                int currentPos = buffer.getCursorPosition();
                buffer.setAttribute(currentPos, attrValue);
            }
        }
        
        return pos;
    }
    
    public int processInsertCursor(byte[] data, int index, int length) {
        // Insert Cursor order - just move to next position for now
        return index + 1;
    }
    
    public int processProgramTab(byte[] data, int index, int length) {
        // Program Tab - move to next unprotected field
        int currentPos = buffer.getCursorPosition();
        int nextField = buffer.findNextUnprotectedField(currentPos);
        buffer.setCursorPosition(nextField);
        
        return index + 1;
    }
    
    public int processRepeatToAddress(byte[] data, int index, int length) {
        if (index + 3 >= length) return index + 1;
        
        int address = decodeAddress(data[index + 1], data[index + 2]);
        byte character = data[index + 3];
        
        int currentPos = buffer.getCursorPosition();
        
        // Repeat character from current position to specified address
        while (currentPos != address && currentPos < buffer.getBufferSize()) {
            buffer.setCharacter(currentPos, (char) character);
            currentPos = (currentPos + 1) % buffer.getBufferSize();
        }
        
        buffer.setCursorPosition(currentPos);
        
        return index + 4;
    }
    
    public int processGraphicsEscape(byte[] data, int index, int length) {
        if (index + 1 >= length) return index + 1;
        
        // Graphics Escape - treat next byte as character
        byte character = data[index + 1];
        int currentPos = buffer.getCursorPosition();
        
        buffer.setCharacter(currentPos, (char) character);
        buffer.setCursorPosition((currentPos + 1) % buffer.getBufferSize());
        
        return index + 2;
    }
    
    public int processStructuredField(byte[] data, int index, int length) {
        if (index + 2 >= length) return index + 1;
        
        // Skip structured field for now
        int fieldLength = ((data[index] & 0xFF) << 8) | (data[index + 1] & 0xFF);
        return index + fieldLength;
    }
    
    public int processCharacter(byte[] data, int index, int length) {
        if (index >= length) return index;
        
        byte b = data[index];
        char character = (char) (b & 0xFF);
        
        int currentPos = buffer.getCursorPosition();
        
        // Only place character if position is not protected
        if (!buffer.isProtected(currentPos)) {
            buffer.setCharacter(currentPos, character);
        }
        
        // Move cursor to next position
        buffer.setCursorPosition((currentPos + 1) % buffer.getBufferSize());
        
        return index + 1;
    }
    
    private int decodeAddress(byte byte1, byte byte2) {
        // 3270 address decoding
        int addr1 = (byte1 & 0x3F);
        int addr2 = (byte2 & 0x3F);
        return (addr1 << 6) | addr2;
    }
    
    public boolean isRunning() {
        return running;
    }
}