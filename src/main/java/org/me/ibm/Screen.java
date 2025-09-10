package org.me.ibm;

import java.io.IOException;
import java.io.OutputStream;

public class Screen {
    private final Buffer buffer;
    private final OutputStream outputStream;
    private boolean insertMode;
    
    public Screen(Buffer buffer, OutputStream outputStream) {
        this.buffer = buffer;
        this.outputStream = outputStream;
        this.insertMode = false;
    }
    
    public String getString() {
        return getString("\n");
    }
    
    public String getString(String separator) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < buffer.getHeight(); row++) {
            if (row > 0) {
                sb.append(separator);
            }
            sb.append(getString(row));
        }
        return sb.toString();
    }
    
    public String getString(int row) {
        if (row < 0 || row >= buffer.getHeight()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        int startPos = row * buffer.getWidth();
        for (int col = 0; col < buffer.getWidth(); col++) {
            sb.append(buffer.getCharacter(startPos + col));
        }
        return sb.toString().replaceAll("\\s+$", ""); // Trim trailing spaces
    }
    
    public String getString(int position, int length) {
        if (position < 0 || position >= buffer.getBufferSize() || length <= 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length && (position + i) < buffer.getBufferSize(); i++) {
            sb.append(buffer.getCharacter(position + i));
        }
        return sb.toString();
    }
    
    public String getString(int row, int col, int length) {
        if (row < 0 || row >= buffer.getHeight() || col < 0 || col >= buffer.getWidth()) {
            return "";
        }
        
        int position = row * buffer.getWidth() + col;
        return getString(position, length);
    }
    
    public void putString(String text) {
        putString(buffer.getCursorPosition(), text);
    }
    
    public void putString(int position, String text) {
        if (text == null || text.isEmpty() || position < 0 || position >= buffer.getBufferSize()) {
            return;
        }
        
        int currentPos = position;
        for (int i = 0; i < text.length() && currentPos < buffer.getBufferSize(); i++) {
            char ch = text.charAt(i);
            
            // Check if position is protected
            if (!buffer.isProtected(currentPos)) {
                if (insertMode) {
                    // In insert mode, shift characters to the right within the field
                    shiftCharactersRight(currentPos);
                }
                buffer.setCharacter(currentPos, ch);
            }
            currentPos++;
        }
        
        // Update cursor position
        buffer.setCursorPosition(Math.min(currentPos, buffer.getBufferSize() - 1));
    }
    
    private void shiftCharactersRight(int startPos) {
        // Find the end of the current field
        int endPos = findFieldEnd(startPos);
        
        // Shift characters right from end to start
        for (int i = endPos - 1; i > startPos; i--) {
            if (!buffer.isProtected(i)) {
                buffer.setCharacter(i, buffer.getCharacter(i - 1));
            }
        }
    }
    
    private int findFieldEnd(int position) {
        for (int i = position + 1; i < buffer.getBufferSize(); i++) {
            if (buffer.isFieldStart(i)) {
                return i;
            }
        }
        return buffer.getBufferSize();
    }
    
    public void pf1() throws IOException {
        sendAID(TelnetConstants.AID_PF1);
    }
    
    public void pf2() throws IOException {
        sendAID(TelnetConstants.AID_PF2);
    }
    
    public void pf3() throws IOException {
        sendAID(TelnetConstants.AID_PF3);
    }
    
    public void pf4() throws IOException {
        sendAID(TelnetConstants.AID_PF4);
    }
    
    public void pf5() throws IOException {
        sendAID(TelnetConstants.AID_PF5);
    }
    
    public void pf6() throws IOException {
        sendAID(TelnetConstants.AID_PF6);
    }
    
    public void pf7() throws IOException {
        sendAID(TelnetConstants.AID_PF7);
    }
    
    public void pf8() throws IOException {
        sendAID(TelnetConstants.AID_PF8);
    }
    
    public void pf9() throws IOException {
        sendAID(TelnetConstants.AID_PF9);
    }
    
    public void pf10() throws IOException {
        sendAID(TelnetConstants.AID_PF10);
    }
    
    public void pf11() throws IOException {
        sendAID(TelnetConstants.AID_PF11);
    }
    
    public void pf12() throws IOException {
        sendAID(TelnetConstants.AID_PF12);
    }
    
    public void enter() throws IOException {
        sendAID(TelnetConstants.AID_ENTER);
    }
    
    public void clear() throws IOException {
        buffer.clear();
        sendAID(TelnetConstants.AID_CLEAR);
    }
    
    public void pa1() throws IOException {
        sendAID(TelnetConstants.AID_PA1);
    }
    
    public void pa2() throws IOException {
        sendAID(TelnetConstants.AID_PA2);
    }
    
    public void pa3() throws IOException {
        sendAID(TelnetConstants.AID_PA3);
    }
    
    public void insert() {
        insertMode = !insertMode;
    }
    
    public boolean isInsertMode() {
        return insertMode;
    }
    
    public void tab() {
        int currentPos = buffer.getCursorPosition();
        int nextField = buffer.findNextUnprotectedField(currentPos);
        buffer.setCursorPosition(nextField);
    }
    
    public void home() {
        buffer.setCursorPosition(0);
        // Move to first unprotected field if one exists
        int firstUnprotected = buffer.findNextUnprotectedField(-1);
        if (firstUnprotected >= 0) {
            buffer.setCursorPosition(firstUnprotected);
        }
    }
    
    private void sendAID(byte aid) throws IOException {
        if (outputStream == null) {
            return;
        }
        
        // Send AID followed by cursor address and modified fields
        outputStream.write(aid);
        
        // Send cursor position (2 bytes)
        int cursorPos = buffer.getCursorPosition();
        outputStream.write(encodeAddress(cursorPos));
        
        // Send modified fields
        sendModifiedFields();
        
        // Send IAC EOR to end transmission
        outputStream.write(TelnetConstants.IAC);
        outputStream.write(TelnetConstants.SE); // Using SE as EOR marker
        outputStream.flush();
    }
    
    private byte[] encodeAddress(int address) {
        // 3270 address encoding (14-bit address in 2 bytes)
        byte[] encoded = new byte[2];
        encoded[0] = (byte) (0x40 | ((address >> 8) & 0x3F));
        encoded[1] = (byte) (0x40 | (address & 0x3F));
        return encoded;
    }
    
    private void sendModifiedFields() throws IOException {
        // Find and send all modified fields
        for (int i = 0; i < buffer.getBufferSize(); i++) {
            if (buffer.isFieldStart(i)) {
                byte attr = buffer.getAttribute(i);
                if ((attr & TelnetConstants.ATTR_MDT) != 0) {
                    // This field is modified, send it
                    sendField(i);
                }
            }
        }
    }
    
    private void sendField(int fieldStart) throws IOException {
        // Send field address
        outputStream.write(encodeAddress(fieldStart));
        
        // Send field data until next field or end of buffer
        int pos = fieldStart + 1; // Skip field attribute
        while (pos < buffer.getBufferSize() && !buffer.isFieldStart(pos)) {
            char ch = buffer.getCharacter(pos);
            if (ch != ' ' || pos == fieldStart + 1) { // Don't send trailing spaces except first char
                outputStream.write((byte) ch);
            }
            pos++;
        }
    }
    
    public Buffer getBuffer() {
        return buffer;
    }
}