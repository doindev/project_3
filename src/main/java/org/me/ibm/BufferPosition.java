package org.me.ibm;

public class BufferPosition {
    private final int position;
    private final int row;
    private final int col;
    
    public BufferPosition(int position) {
        this.position = position;
        this.row = position / TelnetConstants.SCREEN_WIDTH;
        this.col = position % TelnetConstants.SCREEN_WIDTH;
    }
    
    public BufferPosition(int row, int col) {
        this.row = row;
        this.col = col;
        this.position = row * TelnetConstants.SCREEN_WIDTH + col;
    }
    
    public int getPosition() {
        return position;
    }
    
    public int getRow() {
        return row;
    }
    
    public int getCol() {
        return col;
    }
    
    public boolean isValid() {
        return position >= 0 && position < TelnetConstants.BUFFER_SIZE &&
               row >= 0 && row < TelnetConstants.SCREEN_HEIGHT &&
               col >= 0 && col < TelnetConstants.SCREEN_WIDTH;
    }
    
    @Override
    public String toString() {
        return String.format("BufferPosition[pos=%d, row=%d, col=%d]", position, row, col);
    }
}