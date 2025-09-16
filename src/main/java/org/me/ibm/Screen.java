package org.me.ibm;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class Screen {
	private TimeUnit UNIT = TimeUnit.MILLISECONDS;
	private long WAIT = 5000;
	private static final long idleTime = 50; // milliseconds
	private boolean debug = false;
	
    private final Buffer buffer;
    private final OutputStream outputStream;
    private boolean insertMode;
    
    public Screen(Buffer buffer, OutputStream outputStream) {
        this.buffer = buffer;
        this.outputStream = outputStream;
        this.insertMode = false;
    }
    
    public Screen execute(Consumer<Screen> action) {
    	action.accept(this);
		return this;
	}
    
    public String getString() {
    	try {
    		return getString("\n");
    	} catch(Exception e) {
    		return "";
    	}
    }
    
    public String getString(String separator) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < buffer.getHeight(); row++) {
            if (row > 0) {
                sb.append(separator);
            }
            sb.append(getString(row));
        }
        return sb.toString();
    }
    
    public String getString(int row) throws Exception{
        if (row < 0 || row >= buffer.getHeight()) {
            return "";
        }
        
        boolean gotLock = false;
		
		try{
			gotLock = buffer.acquireLock(WAIT, UNIT);
			return buffer.string(row);
		}finally{
			if(gotLock){
				buffer.unlock();
			}
		}
    }
    
    public String getString(int position, int length) throws Exception {
        if (position < 0 || position >= buffer.getBufferSize() || length <= 0) {
            return "";
        }
        
        boolean gotLock = false;
		
		try{
			gotLock = buffer.acquireLock(WAIT, UNIT);
			return buffer.string(position, length);
		}finally{
			if(gotLock){
				buffer.unlock();
			}
		}
    }
    
    public String getString(int row, int col, int length) throws Exception {
        if (row < 0 || row >= buffer.getHeight() || col < 0 || col >= buffer.getWidth()) {
            return "";
        }
        
        return getString((col*TelnetConstants.SCREEN_WIDTH)+row, length);
    }
    
    public Screen put(String text) throws Exception {return putString(buffer.getCursorPosition(), text);}
    public Screen put(int row, int col, String text) throws Exception {return putString((col*TelnetConstants.SCREEN_WIDTH) + row, text);}
    public Screen put(int position, String text) throws Exception {return putString(position, text);}
    
    public Screen putString(String text) throws Exception {
    	return putString(buffer.getCursorPosition(), text);
    }
    
    public Screen putString(int row, int col, String text) throws Exception {
    	return putString((col*TelnetConstants.SCREEN_WIDTH) + row, text);
    }
    
    public Screen putString(int position, String text) throws Exception {
        if (text == null || text.isEmpty() || position < 0 || position >= buffer.getBufferSize()) {
            return this;
        }
        
        boolean gotLock = false;
		try{
			gotLock = buffer.acquireLock(WAIT, UNIT);
			
			int currentPos = position;
			buffer.setCursorPosition(currentPos);
	        for (int i = 0; i < text.length() && currentPos < buffer.getBufferSize(); i++) {
	            char ch = text.charAt(i);
	            
	            // Check if position is protected
	            if (!buffer.isProtected(currentPos)) {
	                if (insertMode) {
	                    // In insert mode, shift characters to the right within the field
	                    shiftCharactersRight(currentPos);
	                }
	                buffer.setAsciiCharacter(currentPos, ch);
	            }
	            currentPos++;
	            
	            if(buffer.isFieldStart(currentPos) || buffer.findFieldStart(currentPos -1) != buffer.findFieldStart(currentPos)){
	            	tab();
	            	currentPos = buffer.getCursorPosition();
	            }
	        }
	        
	        // Update cursor position
	        buffer.setCursorPosition(Math.min(currentPos, buffer.getBufferSize() - 1));
			
//			tn3270.notifyDisplay();
			
			return this;
		}finally{
			if(gotLock){
				buffer.unlock();
			}
		}
    }
    
    private void shiftCharactersRight(int startPos) {
        // Find the end of the current field
        int endPos = findFieldEnd(startPos);
        
        // Shift characters right from end to start
        for (int i = endPos - 1; i > startPos; i--) {
            if (!buffer.isProtected(i)) {
                buffer.setAsciiCharacter(i, buffer.getAsciiCharacter(i - 1));
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
    
    public Screen pf1() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF1);
        return this;
    }
    
    public Screen pf2() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF2);
        return this;
    }
    
    public Screen pf3() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF3);
        return this;
    }
    
    public Screen pf4() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF4);
        return this;
    }
    
    public Screen pf5() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF5);
        return this;
    }
    
    public Screen pf6() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF6);
        return this;
    }
    
    public Screen pf7() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF7);
        return this;
    }
    
    public Screen pf8() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF8);
        return this;
    }
    
    public Screen pf9() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF9);
        return this;
    }
    
    public Screen pf10() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF10);
        return this;
    }
    
    public Screen pf11() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF11);
        return this;
    }
    
    public Screen pf12() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PF12);
        return this;
    }
    
    public Screen enter() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_ENTER);
        return this;
    }
    
    public Screen enter(boolean logOnly) throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_ENTER, logOnly);
        return this;
    }
    
    public Screen clear() throws IOException, InterruptedException, TimeoutException {
        buffer.clear();
        sendCommandKey(TelnetConstants.AID_CLEAR);
        return this;
    }
    
    public Screen pa1() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PA1);
        return this;
    }
    
    public Screen pa2() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PA2);
        return this;
    }
    
    public Screen pa3() throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(TelnetConstants.AID_PA3);
        return this;
    }
    
    public Screen insert() {
        insertMode = !insertMode;
        
        return this;
    }
    
    public boolean isInsertMode() {
        return insertMode;
    }
    
    public Screen tab() {
    	if(buffer.hasFields() == false) {
    		return this;
    	}
    	
        int currentPos = buffer.getCursorPosition();
        int nextField = buffer.findNextUnprotectedField(currentPos);
        
        if(nextField >=0) {
        	buffer.setCursorPosition(nextField);
        }
        return this;
    }
    
    public Screen home() {
    	if(!buffer.hasFields()) {
    		buffer.setCursorPosition(0);
    		return this;
    	}
    	
        for(int i=0;i<buffer.getBufferSize();i++){
			if(buffer.isFieldStart(i)){
				buffer.setCursorPosition(i+1);
				break;
			}
		}
        
        return this;
    }
    
    public Screen sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			assert true;
		}
		return this;
	}
    
    private void sendCommandKey(byte aid) throws IOException, InterruptedException, TimeoutException {
        sendCommandKey(aid, false);
    }
    
    private void sendCommandKey(byte aid, boolean logOnly) throws IOException, InterruptedException, TimeoutException {
        if (outputStream == null) {
            return;
        }
        
        boolean gotLock = false;
        try {
      	  	gotLock = buffer.acquireLock();
        
	        // Send AID followed by cursor address and modified fields
      	  	if(debug) {
				System.out.println("--> " + (aid & 0xff) + " AID");
			}
	        if(!logOnly) {
				outputStream.write(aid);
			}
	        buffer.setAidKey(aid & 0xFF);
	        
	        // Send cursor position (2 bytes)
	        int cursorPos = buffer.getCursorPosition();
	        if(!logOnly) {
				outputStream.write(Tn3270Conversions.getPositionAddress(cursorPos));
			}
	        
	        if(debug) {
	        	System.out.println("--> " + (Tn3270Conversions.getPositionAddress(cursorPos)[0] & 0xFF) + " high");
	        	System.out.println("--> " + (Tn3270Conversions.getPositionAddress(cursorPos)[1] & 0xFF) + " low");
	        }
	        
	        // Send modified fields
	        if(buffer.hasFields()) {
	        	sendModifiedFields();
	        } else {
	        	sendModifiedValues();
	        }
	        
	        
	        // Send IAC EOR to end transmission
	        if(debug) {
				System.out.println("--> " + (TelnetConstants.IAC & 0xFF) + " IAC");
			}
	        if(!logOnly) {
				outputStream.write(TelnetConstants.IAC);
			}
	        
	        if(debug) {
				System.out.println("--> " + (TelnetConstants.EOR & 0xFF) + " EOR");
			}
	        if(!logOnly) {
				outputStream.write(TelnetConstants.EOR);
			}
	        if(!logOnly) {
				outputStream.flush();
			}
	        
	        if(!logOnly) {
				buffer.awaitEor();
			}
        } finally{
        	if(gotLock){
        		buffer.unlock();
        	}
        	try {
        		Thread.sleep(idleTime);
        	} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
			}
        }
    }
    
    private void sendModifiedValues() throws IOException {
    	boolean ord_sba = false;
    	
    	byte[] sba = new byte[3];

    	for(int pos=0;pos<buffer.getBufferSize();pos++){
    		if(buffer.isEbcdicModified(pos)) {
    			if(ord_sba) {
    				if(debug) {
						System.out.println("--> " + (buffer.getEbcdicByte(pos) & 0xFF) + " '" + Tn3270Conversions.ebcdicToAscii(buffer.getEbcdicByte(pos)) + "'");
					}

    				outputStream.write(buffer.getEbcdicByte(pos));
				} else {
					sba[0] = TelnetConstants.SBA;
			    	sba[1] = Tn3270Conversions.getPositionAddress(pos)[0];
			    	sba[2] = Tn3270Conversions.getPositionAddress(pos)[1];
			    	ord_sba = true;
			    	
					outputStream.write(sba);
					
			    	if(debug) {
						System.out.println("--> " + (buffer.getEbcdicByte(pos) & 0xFF) + " '" + Tn3270Conversions.ebcdicToAscii(buffer.getEbcdicByte(pos)) + "'");
					}
			    	
    				outputStream.write(buffer.getEbcdicByte(pos));
				}
    		} else {
    			ord_sba = false;
    		}
    	}
    }
    
    private void sendModifiedFields() throws IOException {
        // Find and send all modified fields
        for (int i = 0; i < buffer.getBufferSize(); i++) {
            if (buffer.isFieldStart(i)) {
                if (buffer.getAttribute(i).isModified()) {
                    // This field is modified, send it
                    sendField(i);
                }
            }
        }
    }
    
    private void sendField(int fieldStart) throws IOException {
        // Send field address
    	boolean sbaSent = false;
    	byte[] sba = new byte[3];
    	
    	
    	sba[0] = TelnetConstants.SBA;
    	sba[1] = Tn3270Conversions.getPositionAddress(fieldStart + 1)[0];
    	sba[2] = Tn3270Conversions.getPositionAddress(fieldStart + 1)[1];
        
        // Send field data until next field or end of buffer
        int pos = fieldStart + 1; // Skip field attribute
        while (
        	pos < buffer.getBufferSize() && 
        	!buffer.isFieldStart(pos) && 
        	buffer.getEbcdicByte(pos)!= 0 && // don't send ebcdic NULL (0x00)
        	(
        		buffer.isEbcdicModified(pos) ||
        		buffer.getAttributeAt(pos).isModified()
    		)
    	) { 
        	// Send SBA only once per field but only if characters are being sent
        	if(!sbaSent) {
        		sbaSent = true;
				outputStream.write(sba);
				
        		if(debug) {
	        		System.out.println("--> " + (sba[0] & 0xFF) + " SBA");
	                System.out.println("--> " + (sba[1] & 0xFF) + " high");
	                System.out.println("--> " + (sba[2] & 0xFF) + " low");
        		}
        	}
        	
//        	System.out.println("--> " + (buffer.getEbcdicByte(pos) & 0xFF) + " '" + Tn3270Conversions.ebcdicToAscii(buffer.getEbcdicByte(pos)) + "'");
        	
    		if(buffer.getEbcdicByte(pos)!= 0){
    			if(debug) {
					System.out.println("--> " + (buffer.getEbcdicByte(pos) & 0xFF) + " '" + Tn3270Conversions.ebcdicToAscii(buffer.getEbcdicByte(pos)) + "'");
				}

				outputStream.write(buffer.getEbcdicByte(pos));
    		}
            pos++;
        }
    }
    
    public int getCursorPosition() {
		return buffer.getCursorPosition();
	}
    
    public Buffer buffer() {
        return buffer;
    }

}
