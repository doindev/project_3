package org.me.ibm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Buffer {
	private TimeUnit UNIT = TimeUnit.MILLISECONDS;
	private long WAIT = 5000;
	
	private Lock lock = new ReentrantLock();
	private Condition tn3270Cond = lock.newCondition();
	private Condition eorCond = lock.newCondition();
	
	private final char[] ascii;
    private final byte[] ebcdic;
    private final char[] asciiWriteBuffer;
    private final byte[] ebcdicWriteBuffer;
    private final boolean[] ebcdicModified;
    private final FieldAttribute[] attributes;
    private final boolean[] fieldStarts;
    private int cursorPosition;
    private final List<ScreenUpdateListener> listeners;
    
    private int fieldCount = 0;
    private int ebcdicCount = 0;
    private int orderCount = 0;
    private Byte cmd;
	private Byte wcc;
    private Integer cmdKey = null;
    private int ack = 0;
    private boolean ignoreAckCount = false;
    
    public Buffer() {
    	this.ascii = new char[TelnetConstants.BUFFER_SIZE];
        this.ebcdic = new byte[TelnetConstants.BUFFER_SIZE];
        this.asciiWriteBuffer = new char[TelnetConstants.BUFFER_SIZE];
        this.ebcdicWriteBuffer = new byte[TelnetConstants.BUFFER_SIZE];
        this.ebcdicModified = new boolean[TelnetConstants.BUFFER_SIZE];
        this.attributes = new FieldAttribute[TelnetConstants.BUFFER_SIZE];
        this.fieldStarts = new boolean[TelnetConstants.BUFFER_SIZE];
        this.cursorPosition = 0;
        this.listeners = new ArrayList<>();
        clear();
    }
    
    public void clear() {
    	Arrays.fill(ascii, ' ');
    	Arrays.fill(ebcdic, (byte)0x00);//(byte)0x40); // EBCDIC space
    	Arrays.fill(asciiWriteBuffer, ' ');
    	Arrays.fill(ebcdicWriteBuffer, (byte)0x00);//(byte)0x40); // EBCDIC space
    	Arrays.fill(ebcdicModified, false);
    	Arrays.fill(attributes, new FieldAttribute());
    	Arrays.fill(fieldStarts, false);

        cursorPosition = 0;
        fieldCount = 0;
        ebcdicCount = 0;
        orderCount = 0;
        ack = 0;
    }
    
    public void clearPreCommandCounts() {
    	orderCount = 0;
		ebcdicCount = 0;
		ack = 0;
    }
    
    public void copyDataToBackground() {
    	System.arraycopy(ascii, 0, asciiWriteBuffer, 0, ascii.length);
    	System.arraycopy(ebcdic, 0, ebcdicWriteBuffer, 0, ebcdic.length);
    	
    	Arrays.fill(ascii, ' ');
    	Arrays.fill(ebcdic, (byte)0x00);//(byte)0x40); // EBCDIC space
    }
    
    public void restoreDataFromBackground() {
		if(hasFields()) {
			for(int i=0;i<TelnetConstants.BUFFER_SIZE;i++) {
				if(fieldStarts[i]) {
					int endIndex = (i+1<TelnetConstants.BUFFER_SIZE?findNextField(i+1):TelnetConstants.BUFFER_SIZE-1);
					
					if(endIndex!= -1) {
						boolean isNull = true;
						
						if(endIndex>i) {
							for(int j=i;j<endIndex;j++) {
								if(ebcdic[j] != 0x00) {
									isNull = false;
									break;
								}
							}
							
							if(isNull) {
//								System.err.println("Restoring field at =============================== " + i + " to " + endIndex + " length: " + (endIndex - i) + "\t, text: \"" + new String(asciiWriteBuffer, i, endIndex - i) + "\"");
								System.arraycopy(asciiWriteBuffer, i, ascii, i, endIndex - i);
						    	System.arraycopy(ebcdicWriteBuffer, i, ebcdic, i, endIndex - i);
							}
						} else {
							// test until the end of the buffer
							for(int j=i;j<TelnetConstants.BUFFER_SIZE;j++) {
								if(ebcdic[j] != 0x00) {
									isNull = false;
									break;
								}
							}
							
							// test from beginning until the beginning of the field
							for(int j=0;j<endIndex;j++) {
								if(ebcdic[j] != 0x00) {
									isNull = false;
									break;
								}
							}
							
							if(isNull) {
//								System.err.println("Restoring field at ++++++++++++++++++++++++++++++ " + i + " to " + (TelnetConstants.BUFFER_SIZE - i) + " length, text: " + new String(asciiWriteBuffer, i, TelnetConstants.BUFFER_SIZE - i) + " and 0 to " + endIndex + " length, text: \"" + new String(asciiWriteBuffer, 0, endIndex) + "\"");
								System.arraycopy(asciiWriteBuffer, i, ascii, i, TelnetConstants.BUFFER_SIZE - i);
						    	System.arraycopy(ebcdicWriteBuffer, i, ebcdic, i, TelnetConstants.BUFFER_SIZE - i);
						    	
//						    	System.err.println("Restoring field at ++++++++++++++++++++++++++++++ 0 to " + endIndex + " length, text: \"" + new String(asciiWriteBuffer, 0, endIndex) + "\"");
						    	System.arraycopy(asciiWriteBuffer, 0, ascii, 0, endIndex);
						    	System.arraycopy(ebcdicWriteBuffer, 0, ebcdic, 0, endIndex);
							}
						}
						
						if(endIndex>i) {
							i = endIndex ;
						}
					}
				}
			}
		}
	}
    
    public void resetMdtFlags() {
    	try {
    		if(!hasFields()) {
				return;
			}
    		
    		int currentFiledStart = -1;
    		FieldAttribute currentAttribute = null;
			for(int i=0;i<TelnetConstants.BUFFER_SIZE;i++) {
				int fieldStart = findFieldStart(i);
				
				// -1 indicates no field start found, so we can stop processing
				if(fieldStart<0) {
					if(
						currentAttribute!=null && 
						!currentAttribute.isProtected() &&
						currentAttribute.isModified()
					) {
						attributes[currentFiledStart].modified(false);
					}
					continue;
				}
				
				if(fieldStart!=currentFiledStart) {
					if(
						currentAttribute!=null && 
						!currentAttribute.isProtected() &&
						currentAttribute.isModified()
					) {
						attributes[currentFiledStart].modified(false);
					}
					currentFiledStart = fieldStart;
					currentAttribute = attributes[fieldStart];
				}
			}
			
			if(
				currentAttribute!=null && 
				!currentAttribute.isProtected() &&
				currentAttribute.isModified()
			) {
				attributes[currentFiledStart].modified(false);
			}
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
	}
    
    public void eraseAllUnprotected() {
    	try {
    		if(!hasFields()) {
				return;
			}
    		
    		int currentFiledStart = -1;
    		FieldAttribute currentAttribute = null;
			for(int i=0;i<TelnetConstants.BUFFER_SIZE;i++) {
				int fieldStart = findFieldStart(i);
				
				// -1 indicates no field start found, so we can stop processing
				if(fieldStart<0) {
					continue;
				}
				
				if(fieldStart!=currentFiledStart) {
					currentFiledStart = fieldStart;
					currentAttribute = attributes[fieldStart];
				}

				if(currentAttribute.isProtected()) {
					continue;
				}
				
				setEbcdicCharacter(i, (byte)0x00);
			}
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
	}
    
    public Buffer setIncomingCommandByte(Byte cmd){
		this.cmd = cmd;
		return this;
	}
    
    public Buffer setIncomingWriteControlCharacterByte(Byte wcc){
		this.wcc = wcc;
		return this;
	}
    
    public Buffer setAidKey(Integer cmdKey){
    	clearPreCommandCounts();
    	this.cmdKey = cmdKey;
    	return this;
    }

    public Buffer incOrderCount(){
		orderCount++;
		return this;
	}
    
    public boolean hasFields() {
    	return fieldCount>0;
    }
    
    public Byte wcc() {
    	return wcc;
    }
    
    protected void awaitEor() throws InterruptedException{
		eorCond.await(WAIT, UNIT);
	}
	public void awaitEor(long wait, TimeUnit unit) throws InterruptedException{
		eorCond.await(wait, unit);
	}
	public void signalEor(){
		if(
			cmd == null ||
			cmdKey==null ||	// this only happens on the first data stream after telnet negotiation
			(wcc != null && (wcc & TelnetConstants.WCC_START_PRINTER) != 0) || // indicates page is rendered
			(ack>0 || ignoreAckCount) || // if ack is greater than 0, we have already signaled once, ignore AckCount was added specifically for ?
			(orderCount>0 && ebcdicCount>1) || // generally means there is something to display
			(orderCount==0 && 
				(
					cmdKey == TelnetConstants.AID_PA1 ||
					cmdKey == TelnetConstants.AID_PA2 ||
					cmdKey == TelnetConstants.AID_PA3 ||
					cmdKey == TelnetConstants.AID_CLEAR
				)
			)
		) {
//			if (cmd != null && cmd == TelnetConstants.WRITE && wcc != null && (wcc & TelnetConstants.WCC_START_PRINTER) != 0) {
//				restoreDataFromBackground();
//			}
//			System.out.println("-".repeat(getWidth()) + "\n" + string("\n") + "-".repeat(getWidth()));
//			System.out.println("signalEor: cmd=" + (cmd!=null?(cmd & 0xff):"null") + ", wcc=" + (wcc!=null?(wcc & 0xff):"null") + ", orders=" + orderCount + ", ebcdic=" + ebcdicCount + ", ack=" + ack + ", cmdKey=" + cmdKey + ", ignoreAckCount=" + ignoreAckCount + 
//					(wcc!=null && (wcc & TelnetConstants.WCC_START_PRINTER)!=0?" START_PRINTER":"") 
//				);
			
			eorCond.signalAll();
			return;
		}
		
//		System.out.println("signalEor: cmd=" + (cmd!=null?(cmd & 0xff):"null") + ", wcc=" + (wcc!=null?(wcc & 0xff):"null") + ", orders=" + orderCount + ", ebcdic=" + ebcdicCount + ", ack=" + ack + ", cmdKey=" + cmdKey + ", ignoreAckCount=" + ignoreAckCount + 
//				(wcc!=null && (wcc & TelnetConstants.WCC_START_PRINTER)!=0?" START_PRINTER":"") 
//			);
		
		ack++;
	}
	
	public void awaitTn3270() throws InterruptedException{
		tn3270Cond.await(WAIT, UNIT);
	}
	public void awaitTn3270(long wait, TimeUnit unit) throws InterruptedException{
		tn3270Cond.await(wait, unit);
	}
	public void signalTn3270(){
		tn3270Cond.signalAll();
	}
    
    public void setEbcdicCharacter(int position, byte ebcdicByte) {
    	ebcdicCount++;
        if (isValidPosition(position)) {
            ebcdic[position] = ebcdicByte;// == 0? 0x40: ebcdicByte; // Treat null as EBCDIC space
            ascii[position] = Tn3270Conversions.ebcdicToAscii(ebcdicByte);
            int sf = findFieldStart(position);
            FieldAttribute attribute = getAttribute(sf);
            
            if(attribute.isModified()) {
            	ebcdicModified[position] = true;
            }
        }
    }
    
    public void setAsciiCharacter(int position, char character) {
        if (isValidPosition(position)) {
            ascii[position] = character;
            ebcdic[position] = Tn3270Conversions.asciiToEbcdic(character);
            ebcdicModified[position] = true;
            
            // fieldStart will be -1 if not fields defined
            int fieldStart = findFieldStart(position);
            if(fieldStart>=0) {
            	attributes[fieldStart].modified(true);
            }
        }
    }
    
    public char getAsciiCharacter(int position) {
        return isValidPosition(position) ? ascii[position] : ' ';
    }
    
    public byte getEbcdicByte(int position) {
        return isValidPosition(position) ? ebcdic[position] : 0x40; // EBCDIC space
    }
    
    public void setAttribute(int position, byte attribute) {
        if (isValidPosition(position)) {
            attributes[position] = new FieldAttribute(attribute);
        }
    }
    
    public FieldAttribute getAttribute(int position) {
        return isValidPosition(position) ? attributes[position] : new FieldAttribute();
    }
    
    public FieldAttribute getAttributeAt(int position) {
		return getAttribute(findFieldStart(position));
	}
    
    public void setFieldStart(int position, boolean isFieldStart) {
        if (isValidPosition(position)) {
        	if(isFieldStart) {
				fieldCount++;
			}

            fieldStarts[position] = isFieldStart;
        }
    }
    
    public boolean isFieldStart(int position) {
        return isValidPosition(position) && fieldStarts[position];
    }
    
    public boolean isProtected(int position) {
        if (!isValidPosition(position)) {
			return true;
		}
        
        // Find the field attribute that applies to this position
        int fieldPos = findFieldStart(position);
        if (fieldPos >= 0) {
            return (attributes[findFieldStart(fieldPos)].isProtected());
        }
        return false;
    }
    
    public boolean isEbcdicModified(int position) {
		return isValidPosition(position) && ebcdicModified[position];
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
    
    public int findNextField(int startPosition) {
    	if(hasFields()==false) {
    		return -1;
    	}
    	 
        for (int i = startPosition + 1; i < TelnetConstants.BUFFER_SIZE; i++) {
            if (fieldStarts[i]) {
                return i;
            }
        }
        // Wrap around
        for (int i = 0; i <= startPosition; i++) {
            if (fieldStarts[i]) {
                return i;
            }
        }
        return startPosition;
    }
    
    public int findNextUnprotectedField(int startPosition) {
    	if(hasFields()==false) {
    		return -1;
    	}
    	 
        for (int i = startPosition + 1; i < TelnetConstants.BUFFER_SIZE; i++) {
            if (fieldStarts[i] && !isProtected(i + 1)) {
                return i;
            }
        }
        // Wrap around
        for (int i = 0; i <= startPosition; i++) {
            if (fieldStarts[i] && !isProtected(i + 1)) {
                return i;
            }
        }
        return startPosition;
    }
    
    public int findPreviousUnprotectedField(int startPosition) {
    	if(hasFields()==false) {
    		return -1;
    	}
    	
    	int currentFieldStart = findFieldStart(startPosition);
    	if(currentFieldStart == -1) {
    		return startPosition;
    	}
    	
        for (int i = currentFieldStart - 1; i >= 0; i--) {
            if (fieldStarts[i] && !isProtected(i + 1)) {
                return i;
            }
        }
        
        // Wrap around
        for (int i = TelnetConstants.BUFFER_SIZE -1; i > currentFieldStart; i--) {
            if (fieldStarts[i] && !isProtected(i + 1)) {
                return i;
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
    
    
    
    public String string() {
		return string(null);
	}
	public String string(String separator) {
		StringBuffer sb = new StringBuffer("");
		
		if(separator!=null){
			for(int row=0;row<TelnetConstants.SCREEN_HEIGHT;row++){
				sb.append( string( row ) );
				sb.append(separator);
			}
		}else{
			try{
				sb.append( new String(ascii, 0, TelnetConstants.BUFFER_SIZE) );
			}catch(Exception e){
				for(int row=0;row<TelnetConstants.SCREEN_HEIGHT;row++) {
					sb.append( string( row ) );
				}
			}
		}

		return sb.toString();
	}
	public String string(int row) {
		return string((row*TelnetConstants.SCREEN_WIDTH),TelnetConstants.SCREEN_WIDTH);
	}
	public String string(int x, int y,int length) {
		return string((y*TelnetConstants.SCREEN_WIDTH)+x, length);
	}
	public String string(int start, int length) {
		try{
    		return new String(ascii, start, length);
    	}catch(Exception e){
    		StringBuffer sb = new StringBuffer();
    		
    		for(int i=start;i<start+length;i++){
				try{
					sb.append(new String(ascii, i, 1));
				}catch(Exception ee){
					sb.append(" ");
				}
			}
    		return sb.toString();
    	}
	}
    
	
	public boolean acquireLock() throws InterruptedException, TimeoutException {
		return acquireLock(WAIT, UNIT);
	}
	public boolean acquireLock(long wait, TimeUnit unit) throws InterruptedException, TimeoutException {
		if(wait<=0 || unit==null){
			boolean gotLock = false;
			while(true){
				gotLock = lock.tryLock();
				if(gotLock){
					return true;
				}
			}
		}else{
			try { // ++
				if(!lock.tryLock(wait, unit)){
					throw new TimeoutException("Timed out waiting for buffer access.");
				}else{
					return true;
				}
			} catch (InterruptedException ie) { // ++
				throw new TimeoutException("Timed out waiting for buffer access."); // ++
			} // ++
		}
	}
	
	public void unlock(){
		lock.unlock();
	}
    
    public void copyFrom(Buffer other) {
        System.arraycopy(other.ebcdic, 0, this.ebcdic, 0, TelnetConstants.BUFFER_SIZE);
        System.arraycopy(other.ascii, 0, this.ascii, 0, TelnetConstants.BUFFER_SIZE);
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
