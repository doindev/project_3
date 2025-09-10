package org.me.ibm;

import java.util.ArrayList;
import java.util.List;

public class Buffer {
    private final char[] characters;
    private final byte[] attributes;
    private final boolean[] fieldStarts;
    private int cursorPosition;
    private final List<ScreenUpdateListener> listeners;
    
    public Buffer() {
        this.characters = new char[TelnetConstants.BUFFER_SIZE];
        this.attributes = new byte[TelnetConstants.BUFFER_SIZE];
        this.fieldStarts = new boolean[TelnetConstants.BUFFER_SIZE];
        this.cursorPosition = 0;
        this.listeners = new ArrayList<>();
        clear();
    }
    
    public void clear() {
        for (int i = 0; i < TelnetConstants.BUFFER_SIZE; i++) {
            characters[i] = ' ';
            attributes[i] = 0;
            fieldStarts[i] = false;
        }
        cursorPosition = 0;
    }
    
    public void setCharacter(int position, char character) {
        if (isValidPosition(position)) {
            characters[position] = character;
        }
    }
    
    public char getCharacter(int position) {
        return isValidPosition(position) ? characters[position] : ' ';
    }
    
    public void setAttribute(int position, byte attribute) {
        if (isValidPosition(position)) {
            attributes[position] = attribute;
        }
    }
    
    public byte getAttribute(int position) {
        return isValidPosition(position) ? attributes[position] : 0;
    }
    
    public void setFieldStart(int position, boolean isFieldStart) {
        if (isValidPosition(position)) {
            fieldStarts[position] = isFieldStart;
        }
    }
    
    public boolean isFieldStart(int position) {
        return isValidPosition(position) && fieldStarts[position];
    }
    
    public boolean isProtected(int position) {
        if (!isValidPosition(position)) return true;
        
        // Find the field attribute that applies to this position
        int fieldPos = findFieldStart(position);
        if (fieldPos >= 0) {
            return (attributes[fieldPos] & TelnetConstants.ATTR_PROTECTED) != 0;
        }
        return false;
    }
    
    public int findFieldStart(int position) {
        for (int i = position; i >= 0; i--) {
            if (fieldStarts[i]) {
                return i;
            }
        }
        // If no field start found, check from end of buffer
        for (int i = TelnetConstants.BUFFER_SIZE - 1; i > position; i--) {
            if (fieldStarts[i]) {
                return i;
            }
        }
        return -1;
    }
    
    public int findNextUnprotectedField(int startPosition) {
        for (int i = startPosition + 1; i < TelnetConstants.BUFFER_SIZE; i++) {
            if (fieldStarts[i] && !isProtected(i + 1)) {
                return i + 1; // Return position after field start
            }
        }
        // Wrap around
        for (int i = 0; i <= startPosition; i++) {
            if (fieldStarts[i] && !isProtected(i + 1)) {
                return i + 1;
            }
        }
        return startPosition;
    }
    
    public int getCursorPosition() {
        return cursorPosition;
    }
    
    public void setCursorPosition(int position) {
        if (isValidPosition(position)) {
            this.cursorPosition = position;
        }
    }
    
    public BufferPosition getCursorBufferPosition() {
        return new BufferPosition(cursorPosition);
    }
    
    public void setCursorPosition(int row, int col) {
        BufferPosition pos = new BufferPosition(row, col);
        if (pos.isValid()) {
            this.cursorPosition = pos.getPosition();
        }
    }
    
    public int getWidth() {
        return TelnetConstants.SCREEN_WIDTH;
    }
    
    public int getHeight() {
        return TelnetConstants.SCREEN_HEIGHT;
    }
    
    public int getBufferSize() {
        return TelnetConstants.BUFFER_SIZE;
    }
    
    public void addScreenUpdateListener(ScreenUpdateListener listener) {
        listeners.add(listener);
    }
    
    public void removeScreenUpdateListener(ScreenUpdateListener listener) {
        listeners.remove(listener);
    }
    
    public void notifyScreenUpdate() {
        for (ScreenUpdateListener listener : listeners) {
            listener.onScreenUpdate();
        }
    }
    
    private boolean isValidPosition(int position) {
        return position >= 0 && position < TelnetConstants.BUFFER_SIZE;
    }
    
    public void copyFrom(Buffer other) {
        System.arraycopy(other.characters, 0, this.characters, 0, TelnetConstants.BUFFER_SIZE);
        System.arraycopy(other.attributes, 0, this.attributes, 0, TelnetConstants.BUFFER_SIZE);
        System.arraycopy(other.fieldStarts, 0, this.fieldStarts, 0, TelnetConstants.BUFFER_SIZE);
        this.cursorPosition = other.cursorPosition;
        notifyScreenUpdate();
    }
    
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Buffer Debug Info:\n");
        sb.append("Cursor Position: ").append(cursorPosition).append("\n");
        sb.append("Field Starts: ");
        for (int i = 0; i < TelnetConstants.BUFFER_SIZE; i++) {
            if (fieldStarts[i]) {
                sb.append(i).append(" ");
            }
        }
        sb.append("\n");
        return sb.toString();
    }
}