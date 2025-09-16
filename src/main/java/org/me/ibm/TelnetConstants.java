package org.me.ibm;

public class TelnetConstants {
    // Telnet Protocol Constants
    public static final byte IAC = (byte) 0xFF;   // Interpret as Command
    public static final byte DONT = (byte) 0xFE;
    public static final byte DO = (byte) 0xFD;
    public static final byte WONT = (byte) 0xFC;
    public static final byte WILL = (byte) 0xFB;
    public static final byte SB = (byte) 0xFA;    // Subnegotiation Begin
    public static final byte SE = (byte) 0xF0;    // Subnegotiation End
    
    // Telnet Options
    public static final byte BINARY = 0;
    public static final byte ECHO = 1;
    public static final byte SUPPRESS_GO_AHEAD = 3;
    public static final byte TERMINAL_TYPE = 24;
    public static final byte END_OF_RECORD = 25;
    public static final byte FORCE_LOGOUT = (byte) 0x12;
    public static final byte EOR = (byte) 0xEF;   // eor
    
    // Terminal Type
    public static final String TERMINAL_TYPE_IBM3278 = "IBM-3278-2-E";
    public static final String TERMINAL_TYPE_IBM3279 = "IBM-3279-2-E";
    
    // 3270 Command Codes
    public static final byte WRITE = (byte) 0xF1;
    public static final byte ERASE_WRITE = (byte) 0xF5;
    public static final byte ERASE_WRITE_ALTERNATE = (byte) 0x7E;
    public static final byte READ_BUFFER = (byte) 0xF2;
    public static final byte READ_MODIFIED = (byte) 0xF6;
    public static final byte READ_MODIFIED_ALL = (byte) 0x6E;
    
    // 3270 Orders
    public static final byte SF = 0x1D;    // Start Field
    public static final byte SFE = 0x29;   // Start Field Extended
    public static final byte SBA = 0x11;   // Set Buffer Address
    public static final byte SA = 0x28;    // Set Attribute
    public static final byte MF = 0x2C;    // Modify Field
    public static final byte IC = 0x13;    // Insert Cursor
    public static final byte PT = 0x05;    // Program Tab
    public static final byte RA = 0x3C;    // Repeat to Address
    public static final byte EUA = 0x12;   // Erase Unprotected to Address
    public static final byte GE = 0x08;    // Graphics Escape
    
    // Write Control Characters
    public static final byte WCC_KEYBOARD_RESTORE = 0x01; // bit 0
    public static final byte WCC_SOUND_ALARM = 0x02; // bit 1
    public static final byte WCC_START_PRINTER = 0x04; // bit 2
    public static final byte WCC_PRINT = 0x08;// bit 3
    public static final byte WCC_ERASE_ALL_UNPROTECTED = 0x40; // bit 6 or (byte)64
    
    // Field Attributes
    public static final byte ATTR_PROTECTED = 0x20;
    public static final byte ATTR_NUMERIC = 0x10;
    public static final byte ATTR_DISPLAY_MASK = 0x0C;
    public static final byte ATTR_INTENSITY_MASK = 0x08;
    public static final byte ATTR_MDT = 0x01;  // Modified Data Tag
    
    // Display Attributes
    public static final byte DISPLAY_NORMAL = 0x00;
    public static final byte DISPLAY_INVISIBLE = 0x08;
    public static final byte DISPLAY_INTENSE = 0x04;
    public static final byte DISPLAY_NONDISPLAY = 0x0C;
    
    // Structured Field Orders
    public static final byte SF_READ_PARTITION = 0x01;
    public static final byte SF_ACTIVATE_PARTITION = 0x0E;
    public static final byte SF_DESTROY_PARTITION = 0x0F;
    
    // AID (Attention Identifier) Codes
    public static final byte AID_CLEAR = 0x6D;
    public static final byte AID_ENTER = 0x7D;
    public static final byte AID_PF1 = (byte) 0xF1;
    public static final byte AID_PF2 = (byte) 0xF2;
    public static final byte AID_PF3 = (byte) 0xF3;
    public static final byte AID_PF4 = (byte) 0xF4;
    public static final byte AID_PF5 = (byte) 0xF5;
    public static final byte AID_PF6 = (byte) 0xF6;
    public static final byte AID_PF7 = (byte) 0xF7;
    public static final byte AID_PF8 = (byte) 0xF8;
    public static final byte AID_PF9 = (byte) 0xF9;
    public static final byte AID_PF10 = (byte) 0x7A;
    public static final byte AID_PF11 = (byte) 0x7B;
    public static final byte AID_PF12 = (byte) 0x7C;
    public static final byte AID_PA1 = (byte) 0x6C;
    public static final byte AID_PA2 = (byte) 0x6E;
    public static final byte AID_PA3 = (byte) 0x6B;
    
    // Buffer size for IBM-3278-2-E (24x80)
    public static final int SCREEN_WIDTH = 80;
    public static final int SCREEN_HEIGHT = 24;
    public static final int BUFFER_SIZE = SCREEN_WIDTH * SCREEN_HEIGHT;
}
