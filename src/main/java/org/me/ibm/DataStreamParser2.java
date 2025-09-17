package org.me.ibm;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeoutException;

public class DataStreamParser2 implements IDataStreamParser{
    private final Buffer buffer;
    private final InputStream inputStream;
    private volatile boolean running;
//    private boolean debug = false;
    
    // Current command being processed
    private byte currentCommand;
    
    public DataStreamParser2(Buffer buffer, InputStream inputStream) {
        this.buffer = buffer;
        this.inputStream = inputStream;
        this.running = false;
    }
    
    public void parse() throws IOException {
        // Read first command byte from stream
        int firstByte = inputStream.read();
        if (firstByte == -1) {
            throw new IOException("No data available to parse");
        }
        parse(firstByte);
    }
    
    @Override
	public void parse(int firstByte) throws IOException {
        running = true;
        
        // Validate and process first byte as command
        byte command = (byte) firstByte;
        if ((command & 0xFF) < 240) {
            // Convert to 3270 command range if needed
            command = (byte)((command & 0xFF) + 240);
        }
        
        // Validate it's a valid 3270 command
        if (!isValid3270Command(command)) {
            throw new IOException("Invalid 3270 command byte: " + (command & 0xFF));
        }
        
        // Process commands in a continuous loop
        while (running) {
            try {
                // Process the current command
                processCommand(command);
                
                // After processing, we should have just read IAC EOR
                // Now read the next command byte
                int nextByte = inputStream.read();
                if (nextByte == -1) {
                    // End of stream
                    break;
                }
                
                command = (byte) nextByte;
                if ((command & 0xFF) < 240) {
                    command = (byte)((command & 0xFF) + 240);
                }
                
                if (!isValid3270Command(command)) {
                    throw new IOException("Invalid 3270 command byte: " + (command & 0xFF));
                }
                
            } catch (IOException e) {
                if (running) {
                    throw e; // Only throw if we're still supposed to be running
                }
                break;
            }
        }
    }
    
    private boolean isValid3270Command(byte command) {
        switch (command) {
            case TelnetConstants.WRITE:
            case TelnetConstants.ERASE_WRITE:
            case TelnetConstants.ERASE_WRITE_ALTERNATE:
            case TelnetConstants.READ_BUFFER:
            case TelnetConstants.READ_MODIFIED:
            case TelnetConstants.READ_MODIFIED_ALL:
                return true;
            default:
                return false;
        }
    }
    
    @Override
	public void stop() {
        running = false;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    private byte readByte() throws IOException {
        int b = inputStream.read();
        if (b == -1) {
            throw new IOException("Unexpected end of stream");
        }
        return (byte) b;
    }
    
    private void processCommand(byte command) throws IOException {
        currentCommand = command;
        
        boolean gotLock = false;
        try {
            gotLock = buffer.acquireLock();
            
            buffer.setIncomingCommandByte(command);
            
            switch (command) {
                case TelnetConstants.WRITE:
//                    if(debug) {
//                        System.out.println((command & 0xFF) + " CMD_WRITE");
//                    }
                    processWrite();
                    // only call restore if start printer was set
                    if (buffer.wcc() != null && (buffer.wcc() & TelnetConstants.WCC_START_PRINTER) != 0) {
                        buffer.restoreDataFromBackground();
                    }
                    break;
                case TelnetConstants.ERASE_WRITE:
//                    if(debug) {
//                        System.out.println((command & 0xFF) + " CMD_ERASE_WRITE");
//                    }
                    processWrite();
                    break;
                case TelnetConstants.ERASE_WRITE_ALTERNATE:
//                    if(debug) {
//                        System.out.println((command & 0xFF) + " CMD_ERASE_WRITE_ALTERNATE");
//                    }
                    processWrite();
                    break;
                case TelnetConstants.READ_BUFFER:
//                    if(debug) {
//                        System.out.println((command & 0xFF) + " CMD_READ_BUFFER");
//                    }
                    // Read commands don't have data following them
                    expectIacEor();
                    break;
                case TelnetConstants.READ_MODIFIED:
//                    if(debug) {
//                        System.out.println((command & 0xFF) + " CMD_READ_MODIFIED");
//                    }
                    expectIacEor();
                    break;
                case TelnetConstants.READ_MODIFIED_ALL:
//                    if(debug) {
//                        System.out.println((command & 0xFF) + " CMD_READ_MODIFIED_ALL");
//                    }
                    expectIacEor();
                    break;
                default:
                    throw new IOException("Unhandled command: " + (command & 0xFF));
            }
            
        } catch (InterruptedException e) {
        	e.printStackTrace();
        } catch (TimeoutException e) {
        	e.printStackTrace();
        } finally {
            if (gotLock) {
            	buffer.signalEor();
                buffer.unlock();
            }
        }
    }
    
    private void expectIacEor() throws IOException {
        byte iac = readByte();
        if (iac != TelnetConstants.IAC) {
            throw new IOException("Expected IAC, got: " + (iac & 0xFF));
        }
        byte eor = readByte();
        if (eor != TelnetConstants.EOR) {
            throw new IOException("Expected EOR after IAC, got: " + (eor & 0xFF));
        }
    }
    
    private void processWrite() throws IOException {
        // Process Write Control Character
        processWriteControlCharacter();
        
        // Process orders and data until IAC EOR
        while (running) {
            byte b = readByte();
            
            // Check for IAC which signals potential end of data
            if (b == TelnetConstants.IAC) {
                byte next = readByte();
                if (next == TelnetConstants.EOR) {
                    // End of this write command
                    return;
                }
                // Not EOR, was actually IAC IAC (escaped IAC)
                // Process single IAC as data
                processDataByte(TelnetConstants.IAC);
                continue;
            }
            
            // Check for orders
            switch (b) {
                case TelnetConstants.SF:
                    buffer.incOrderCount();
                    processStartField();
                    break;
                    
                case TelnetConstants.SFE:
                    buffer.incOrderCount();
                    processStartFieldExtended();
                    break;
                    
                case TelnetConstants.SBA:
                    buffer.incOrderCount();
                    processSetBufferAddress();
                    break;
                    
                case TelnetConstants.SA:
                    buffer.incOrderCount();
                    processSetAttribute();
                    break;
                    
                case TelnetConstants.MF:
                    buffer.incOrderCount();
                    processModifyField();
                    break;
                    
                case TelnetConstants.IC:
                    buffer.incOrderCount();
                    processInsertCursor();
                    break;
                    
                case TelnetConstants.PT:
                    buffer.incOrderCount();
                    processProgramTab();
                    break;
                    
                case TelnetConstants.RA:
                    buffer.incOrderCount();
                    processRepeatToAddress();
                    break;
                    
                case TelnetConstants.EUA:
                    buffer.incOrderCount();
                    processEraseUntilAddress();
                    break;
                    
                case TelnetConstants.GE:
                    buffer.incOrderCount();
                    processGraphicsEscape();
                    break;
                    
                default:
                    // Regular data byte
                    processDataByte(b);
                    break;
            }
        }
    }
    
    private void processWriteControlCharacter() throws IOException {
        byte wcc = readByte();
        
        buffer.setIncomingWriteControlCharacterByte(wcc);
        
//        List<String> wccFlags = new ArrayList<>();
//        
//        if ((wcc & TelnetConstants.WCC_ERASE_ALL_UNPROTECTED) != 0) {
//            wccFlags.add("RESET_MDT");
//            wccFlags.add("ERASE_ALL_UNPROTECTED");
//        }
//        
//        if ((wcc & TelnetConstants.WCC_PRINT) != 0) {
//            wccFlags.add("PRINT");
//        }
//        
//        if ((wcc & TelnetConstants.WCC_START_PRINTER) != 0) {
//            wccFlags.add("START_PRINTER");
//        }
//        
//        if ((wcc & TelnetConstants.WCC_SOUND_ALARM) != 0) {
//            wccFlags.add("SOUND_ALARM");
//        }
//        
//        if ((wcc & TelnetConstants.WCC_KEYBOARD_RESTORE) != 0) {
//            wccFlags.add("KEYBOARD_RESTORE");
//        }
        
        // Handle command-specific WCC processing
        switch(currentCommand) {
        case TelnetConstants.ERASE_WRITE:
        case TelnetConstants.ERASE_WRITE_ALTERNATE:
            // Clear buffer for erase write commands
            buffer.clear();
            break;
        case TelnetConstants.WRITE:
            if ((wcc & TelnetConstants.WCC_ERASE_ALL_UNPROTECTED) != 0) {
                buffer.resetMdtFlags();
                buffer.eraseAllUnprotected();
            }
            
            // Copy to background if start printer is set
            if ((wcc & TelnetConstants.WCC_START_PRINTER) != 0) {
                buffer.copyDataToBackground();
            }
            break;
        }
        
//        if(debug) {
//            System.out.println((wcc & 0xFF) + " WCC [" + String.join(", ", wccFlags) + "]");
//        }
    }
    
    private void processStartField() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_SF");
//        }
        
        byte attribute = readByte();
        
//        if(debug) {
//            System.out.println((attribute & 0xFF) + " attr");
//        }
        
        int currentPos = buffer.getCursorPosition();
        
        buffer.setFieldStart(currentPos, true);
        buffer.setAttribute(currentPos, attribute);
        buffer.setEbcdicCharacter(currentPos, (byte)0x40); // Field attribute position is typically blank
        
        currentPos++;
        buffer.setCursorPosition(Math.min((currentPos<buffer.getBufferSize()?currentPos:0), buffer.getBufferSize() - 1));
    }
    
    private void processStartFieldExtended() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_SFE");
//        }
        
        byte paramCount = readByte();
        
//        if(debug) {
//            System.out.println((paramCount & 0xFF) + " paramCount");
//        }
        
        int currentPos = buffer.getCursorPosition();
        buffer.setFieldStart(currentPos, true);
        
        // Process extended attributes
        for (int i = 0; i < paramCount; i++) {
            byte attrType = readByte();
            byte attrValue = readByte();
            
//            if(debug) {
//                System.out.println((attrType & 0xFF) + " attrType");
//                System.out.println((attrValue & 0xFF) + " attrValue");
//            }
            
            if (attrType == (byte) 0xC0) { // Basic attribute
                buffer.setAttribute(currentPos, attrValue);
            }
            // Handle other extended attributes as needed
        }
        
        buffer.setEbcdicCharacter(currentPos, (byte)0x40);
        
        currentPos++;
        buffer.setCursorPosition(Math.min((currentPos<buffer.getBufferSize()?currentPos:0), buffer.getBufferSize() - 1));
    }
    
    private void processSetBufferAddress() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_SBA");
//        }
        
        byte byte1 = readByte();
        byte byte2 = readByte();
        int address = decodeAddress(byte1, byte2);
        
        if (address >= 0 && address < buffer.getBufferSize()) {
            buffer.setCursorPosition(address);
        }
    }
    
    private void processSetAttribute() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_SA");
//        }
        
        byte attrType = readByte();
        byte attrValue = readByte();
        
//        if(debug) {
//            System.out.println((attrType & 0xFF) + " attrType");
//            System.out.println((attrValue & 0xFF) + " attrValue");
//        }
        
        // Set attribute at current cursor position
        int currentPos = buffer.getCursorPosition();
        if (attrType == (byte) 0xC0) { // Basic attribute
            buffer.setAttribute(currentPos, attrValue);
        }
    }
    
    private void processModifyField() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_MF");
//        }
        
        byte paramCount = readByte();
        
//        if(debug) {
//            System.out.println((paramCount & 0xFF) + " paramCount");
//        }
        
        // Process modify field parameters
        for (int i = 0; i < paramCount; i++) {
            byte attrType = readByte();
            byte attrValue = readByte();
            
//            if(debug) {
//                System.out.println((attrType & 0xFF) + " attrType");
//                System.out.println((attrValue & 0xFF) + " attrValue");
//            }
            
            // Modify field attributes at current position
            if (attrType == (byte) 0xC0) {
                int currentPos = buffer.getCursorPosition();
                buffer.setAttribute(currentPos, attrValue);
            }
        }
    }
    
    private void processInsertCursor() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_IC");
//        }
        
        // Insert Cursor order - just sets cursor position marker
    }
    
    private void processProgramTab() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_PT");
//        }
        
        // Program Tab - move to next unprotected field
        int currentPos = buffer.getCursorPosition();
        int nextField = buffer.findNextUnprotectedField(currentPos) + 1;
        buffer.setCursorPosition(nextField);
    }
    
    private void processRepeatToAddress() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_RA");
//        }
        
        byte byte1 = readByte();
        byte byte2 = readByte();
        int address = decodeAddress(byte1, byte2);
        byte character = readByte();
        
//        if(debug) {
//            System.out.println("'" + Tn3270Conversions.ebcdicToAscii(character) + "' to " + address);
//        }
        
        int currentPos = buffer.getCursorPosition();
        
        // Repeat character from current position to specified address
        while (currentPos != address && currentPos < buffer.getBufferSize()) {
            buffer.setEbcdicCharacter(currentPos, character);
            currentPos = (currentPos + 1) % buffer.getBufferSize();
        }
        
        buffer.setCursorPosition(currentPos);
    }
    
    private void processEraseUntilAddress() throws IOException {
        /*
         * insert null in every field starting at the current address up to a ending position,
         * only if the position is not protected, do not move the cursor
         */
        
//        if(debug) {
//            System.out.println("ORDER_EUA");
//        }
        
        byte byte1 = readByte();
        byte byte2 = readByte();
        int address = decodeAddress(byte1, byte2);
        int currentPos = buffer.getCursorPosition();
        
        while (currentPos != address && currentPos < buffer.getBufferSize()) {
            // Only erase if position is not protected
            if (!buffer.isProtected(currentPos)) {
                buffer.setEbcdicCharacter(currentPos, (byte) 0x00); // EBCDIC null
            }
            currentPos++;
        }
    }
    
    private void processGraphicsEscape() throws IOException {
//        if(debug) {
//            System.out.println("ORDER_GE");
//        }
        
        // Graphics Escape - treat next byte as character
        byte character = readByte();
        
//        if(debug) {
//            System.out.println((character & 0xFF) + " char");
//        }
        
        int currentPos = buffer.getCursorPosition();
        
        buffer.setEbcdicCharacter(currentPos, character);

        currentPos++;
        buffer.setCursorPosition(Math.min((currentPos<buffer.getBufferSize()?currentPos:0), buffer.getBufferSize() - 1));
    }
    
    private void processDataByte(byte b) {
//        if(debug) {
//            System.out.println((b & 0xFF) + " '" + Tn3270Conversions.ebcdicToAscii(b) + "'");
//        }
        
        int currentPos = buffer.getCursorPosition();
        
        buffer.setEbcdicCharacter(currentPos, b);
        
        currentPos++;
        buffer.setCursorPosition(Math.min((currentPos<buffer.getBufferSize()?currentPos:0), buffer.getBufferSize() - 1));
    }
    
    private int decodeAddress(byte byte1, byte byte2) {
        // 3270 address decoding
        int addr1 = (byte1 & 0x3F);
        int addr2 = (byte2 & 0x3F);
        
//        if(debug) {
//            System.out.println((byte1 & 0xFF) + " high");
//            System.out.println((byte2 & 0xFF) + " low");
//            System.out.println(((addr1 << 6) | addr2) + " final pos");
//        }
        
        return (addr1 << 6) | addr2;
    }
}
