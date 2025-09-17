package org.me.ibm;

import java.io.IOException;
import java.io.InputStream;

public class DataStreamParser implements IDataStreamParser{
    private final Buffer buffer;
    private final InputStream inputStream;
    private boolean running;
//    private boolean debug = false;
   
    public DataStreamParser(Buffer buffer, InputStream inputStream) {
        this.buffer = buffer;
        this.inputStream = inputStream;
        this.running = false;
    }
    
    public void parse() throws IOException {
        parse(-1); // No first byte provided
    }
    
    @Override
	public void parse(int firstByte) throws IOException {
        running = true;
        boolean iacMode = false;

        // Process the first byte if provided
        if (firstByte != -1) {
            bufferByte((byte) firstByte);
        }
        
        // Use single byte reads to handle telnet protocol properly
        while (running) {
            try {
                int b = inputStream.read();

                if (b == -1) {
                    break; // End of stream
                }
                
                if(!iacMode) {
                	if(((byte)b) == TelnetConstants.IAC) {
    					iacMode = true;
    					continue; // Read next byte for command
    				}
                } else if(((byte)b) == TelnetConstants.EOR) {
					iacMode = false;
					
					boolean gotLock = false;
					try {
						gotLock = buffer.acquireLock();
						processDataStream(dataBuffer, dataBufferPos); 
					} catch (Exception e) {
						throw new IOException("Error processing data stream at EOR", e);
					} finally {
						dataBufferPos = 0; // Reset data buffer position after processing
						if(gotLock){
							buffer.signalEor();
							buffer.unlock();
						}
					}
					continue; // Read next byte for command
				}
                
                bufferByte((byte) b);
                
            } catch (IOException e) {
                if (running) {
                    throw e; // Only throw if we're still supposed to be running
                }
                break;
            }
        }
    }
    
    private byte[] dataBuffer = new byte[8192];
    private int dataBufferPos = 0;
    
    private void bufferByte(byte b) throws IOException {
    	// This is a normal 3270 data byte
        if (dataBufferPos < dataBuffer.length) {
            dataBuffer[dataBufferPos++] = b;
        } else {
            // Buffer is full, process it
            processDataStream(dataBuffer, dataBufferPos);
            dataBufferPos = 0;
            dataBuffer[dataBufferPos++] = b;
        }
    }
    
    
    @Override
	public void stop() {
        running = false;
    }
    
    
    private void processDataStream(byte[] data, int length) throws IOException {
        int i = 0;

        while (i < length) {
            // Process 3270 command (no more IAC processing here since it's handled at byte level)
            i = processCommand(data, i, length);
        }
    }
    
    
    public int processCommand(byte[] data, int index, int length) throws IOException {
        if (index >= length) {
			return index;
		}
        
        byte command = data[index];
       
        if((command & 0xFF) < 240) {
        	command = (byte)((command & 0xFF) + 240);
        	data[index] = command;
        }
        
        buffer.setIncomingCommandByte(command);
        
        switch (command) {
            case TelnetConstants.WRITE:
//            	if(debug) {
//					System.out.println((command & 0xFF) + " CMD_WRITE");
//				}
                int indx = processWrite(data, index + 1, length);
                // only call restore if start printer was set, meaning the screen data stream is complete
                if (buffer.wcc() != null && (buffer.wcc() & TelnetConstants.WCC_START_PRINTER) != 0) {
                	buffer.restoreDataFromBackground();
                }
                return indx;
            case TelnetConstants.ERASE_WRITE:
//            	if(debug) {
//					System.out.println((command & 0xFF) + " CMD_ERASE_WRITE");
//				}
                return processWrite(data, index + 1, length);
            case TelnetConstants.ERASE_WRITE_ALTERNATE:
//            	if(debug) {
//					System.out.println((command & 0xFF) + " CMD_ERASE_WRITE_ALTERNATE");
//				}
                return processWrite(data, index + 1, length);
            case TelnetConstants.READ_BUFFER:
//            	if(debug) {
//					System.out.println((command & 0xFF) + " CMD_READ_BUFFER");
//				}
            	return index + 1;
            case TelnetConstants.READ_MODIFIED:
//            	if(debug) {
//					System.out.println((command & 0xFF) + " CMD_READ_MODIFIED");
//				}
            	return index + 1;
            case TelnetConstants.READ_MODIFIED_ALL:
//            	if(debug) {
//					System.out.println((command & 0xFF) + " CMD_READ_MODIFIED_ALL");
//				}
                return index + 1;
            default:
                // Treat as data character
                return processCharacter(data, index, length);
        }
    }
    
    private int processWrite(byte[] data, int index, int length) throws IOException {
        if (index >= length) {
			return index;
		}
        
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
                	buffer.incOrderCount();
                    index = processStartField(data, index, length);
                    break;
                    
                case TelnetConstants.SFE:
                	buffer.incOrderCount();
                    index = processStartFieldExtended(data, index, length);
                    break;
                    
                case TelnetConstants.SBA:
                	buffer.incOrderCount();
                    index = processSetBufferAddress(data, index, length);
                    break;
                    
                case TelnetConstants.SA:
                	buffer.incOrderCount();
                    index = processSetAttribute(data, index, length);
                    break;
                    
                case TelnetConstants.MF:
                	buffer.incOrderCount();
                    index = processModifyField(data, index, length);
                    break;
                    
                case TelnetConstants.IC:
                	buffer.incOrderCount();
                    index = processInsertCursor(data, index, length);
                    break;
                    
                case TelnetConstants.PT:
                	buffer.incOrderCount();
                    index = processProgramTab(data, index, length);
                    break;
                    
                case TelnetConstants.RA:
                	buffer.incOrderCount();
                    index = processRepeatToAddress(data, index, length);
                    break;
                case TelnetConstants.EUA:
                	buffer.incOrderCount();
                    index = processEraseUntilAddress(data, index, length);
                    break;
                case TelnetConstants.GE:
                	buffer.incOrderCount();
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
        if (index >= length) {
			return index;
		}
        
        byte wcc = data[index];
       
//        List<String> wccFlags = new ArrayList<>();
//
//        if ((wcc & TelnetConstants.WCC_ERASE_ALL_UNPROTECTED) != 0) {
//			wccFlags.add("RESET_MDT");
//			wccFlags.add("ERASE_ALL_UNPROTECTED");
//        }
//        
//        if ((wcc & TelnetConstants.WCC_PRINT) != 0) {
//        	wccFlags.add("PRINT");
//        }
//        
//        if ((wcc & TelnetConstants.WCC_START_PRINTER) != 0) {
//        	wccFlags.add("START_PRINTER");
//        }
//        
//        if ((wcc & TelnetConstants.WCC_SOUND_ALARM) != 0) {
//        	wccFlags.add("SOUND_ALARM");
//        }
//        
//        if ((wcc & TelnetConstants.WCC_KEYBOARD_RESTORE) != 0) {
//        	wccFlags.add("KEYBOARD_RESTORE");
//        }
        
        buffer.setIncomingCommandByte(data[index -1]);
        buffer.setIncomingWriteControlCharacterByte(wcc);
        
        switch(data[index -1]) {
    	case TelnetConstants.ERASE_WRITE:
		case TelnetConstants.ERASE_WRITE_ALTERNATE:
			// no need to check or call reset mdt or erase all unprotected here, its implied by .clear()
			buffer.clear();
			break;
		case TelnetConstants.WRITE:
			if ((wcc & TelnetConstants.WCC_ERASE_ALL_UNPROTECTED) != 0) {
				buffer.resetMdtFlags();
				buffer.eraseAllUnprotected();
			}
			
			// only call copy to background if start printer is set, meaning the screen data stream is complete
			if ((wcc & TelnetConstants.WCC_START_PRINTER) != 0) {
				buffer.copyDataToBackground();
			}
    	}
        
//        if(debug) {
//			System.out.println((data[index] & 0xFF) + " WCC [" + String.join(", ", wccFlags) + "]");
//		}

        return index + 1;
    }
    
    public int processStartField(byte[] data, int index, int length) {
        if (index + 1 >= length) {
			return index + 1;
		}
        
//        if(debug) {
//			System.out.println((data[index] & 0xFF) + " ORDER_SF");
//		}
         
        byte attribute = data[index + 1];
        
//        if(debug) {
//			System.out.println((attribute & 0xFF) + " attr");
//		}
        
        int currentPos = buffer.getCursorPosition();
        
        buffer.setFieldStart(currentPos, true);
        buffer.setAttribute(currentPos, attribute);
        buffer.setEbcdicCharacter(currentPos, (byte)0x40); // Field attribute position is typically blank
        
        currentPos++;
        buffer.setCursorPosition(Math.min((currentPos<buffer.getBufferSize()?currentPos:0), buffer.getBufferSize() - 1));
        
        return index + 2;
    }
    
    public int processStartFieldExtended(byte[] data, int index, int length) {
        if (index + 1 >= length) {
			return index + 1;
		}
        
//        if(debug) {
//			System.out.println((data[index] & 0xFF) + " ORDER_SFE");
//		}
        
        int pos = index + 1;
        byte paramCount = data[pos++];
        
//        if(debug) {
//			System.out.println((paramCount & 0xFF) + " paramCount");
//		}
        
        int currentPos = buffer.getCursorPosition();
        buffer.setFieldStart(currentPos, true);
        
        // Process extended attributes
        for (int i = 0; i < paramCount && pos + 1 < length; i++) {
            byte attrType = data[pos++];
            byte attrValue = data[pos++];
            
//            if(debug) {
//    			System.out.println((attrType & 0xFF) + " attrType");
//    			System.out.println((attrValue & 0xFF) + " attrValue");
//    		}
            
            if (attrType == (byte) 0xC0) { // Basic attribute
                buffer.setAttribute(currentPos, attrValue);
            }
            // Handle other extended attributes as needed
        }
        
        buffer.setEbcdicCharacter(currentPos, (byte)0x40);
        
        currentPos++;
        buffer.setCursorPosition(Math.min((currentPos<buffer.getBufferSize()?currentPos:0), buffer.getBufferSize() - 1));

        return pos;
    }
    
    public int processSetBufferAddress(byte[] data, int index, int length) {
        if (index + 2 >= length) {
			return index + 1;
		}
        
//        if(debug) {
//        	System.out.println((data[index] & 0xFF) + " ORDER_SBA");
//        }
        
        int address = decodeAddress(data[index + 1], data[index + 2]);
        
        if (address >= 0 && address < buffer.getBufferSize()) {
            buffer.setCursorPosition(address);
        }
        
        return index + 3;
    }
    
    public int processSetAttribute(byte[] data, int index, int length) {
        if (index + 2 >= length) {
			return index + 1;
		}
        
//        if(debug) {
//        	System.out.println((data[index] & 0xFF) + " ORDER_SA");
//        	System.out.println((data[index + 1] & 0xFF) + " attrType");
//        	System.out.println((data[index + 2] & 0xFF) + " attrValue");
//        }
        
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
        if (index + 2 >= length) {
			return index + 1;
		}
        
//        if(debug) {
//        	System.out.println((data[index] & 0xFF) + " ORDER_MF");
//        	System.out.println((data[index + 1] & 0xFF) + " paramCount");
//        }
        
        byte paramCount = data[index + 1];
        int pos = index + 2;
        
        // Process modify field parameters
        for (int i = 0; i < paramCount && pos + 1 < length; i++) {
            byte attrType = data[pos++];
            byte attrValue = data[pos++];
            
//            if(debug) {
//    			System.out.println((attrType & 0xFF) + " attrType");
//    			System.out.println((attrValue & 0xFF) + " attrValue");
//    		}
            
            // Modify field attributes at current position
            if (attrType == (byte) 0xC0) {
                int currentPos = buffer.getCursorPosition();
                buffer.setAttribute(currentPos, attrValue);
            }
        }
        
        return pos;
    }
    
    public int processInsertCursor(byte[] data, int index, int length) {
//    	if(debug) {
//			System.out.println((data[index] & 0xFF) + " ORDER_IC");
//		}
    	
        // Insert Cursor order - just move to next position for now
        return index + 1;
    }
    
    public int processProgramTab(byte[] data, int index, int length) {
//    	if(debug) {
//			System.out.println((data[index] & 0xFF) + " ORDER_PT");
//		}
    	
        // Program Tab - move to next unprotected field
        int currentPos = buffer.getCursorPosition();
        int nextField = buffer.findNextUnprotectedField(currentPos) + 1;
        buffer.setCursorPosition(nextField);
        
        return index + 1;
    }
    
    public int processRepeatToAddress(byte[] data, int index, int length) {
        if (index + 3 >= length) {
			return index + 1;
		}
        
//        if(debug) {
//			System.out.println((data[index] & 0xFF) + " ORDER_RA");
//		}
        
        int address = decodeAddress(data[index + 1], data[index + 2]);
        byte character = data[index + 3];
        
//        if(debug) {
//			System.out.println((data[index] & 0xFF) + " '" + Tn3270Conversions.ebcdicToAscii(character) + "' to " + address);
//		}
        
        int currentPos = buffer.getCursorPosition();
        
        // Repeat character from current position to specified address
        while (currentPos != address && currentPos < buffer.getBufferSize()) {
            buffer.setEbcdicCharacter(currentPos, character);
            currentPos = (currentPos + 1) % buffer.getBufferSize();
        }
        
        buffer.setCursorPosition(currentPos);
        
        return index + 4;
    }
    
    public int processEraseUntilAddress(byte[] data, int index, int length) {
        if (index + 2 >= length) {
			return index + 1;
		}
        
        /*
         * insert null in every field starting at the current address up to a ending position,
         * only if the position is not protected, do not move the cursor
         */
        
//        if(debug) {
//			System.out.println((data[index] & 0xFF) + " ORDER_EUA");
//		}
        
        int address = decodeAddress(data[index + 1], data[index + 2]);
        int currentPos = buffer.getCursorPosition();
        
        while (currentPos != address && currentPos < buffer.getBufferSize()) {
        	// Only erase if position is not protected
			if (!buffer.isProtected(currentPos)) {
				buffer.setEbcdicCharacter(currentPos, (byte) 0x00); // EBCDIC null
			}
			currentPos++;
        }
        
        return index + 3;
    }
    
    public int processGraphicsEscape(byte[] data, int index, int length) {
        if (index + 1 >= length) {
			return index + 1;
		}
        
//        if(debug) {
//        	System.out.println((data[index] & 0xFF) + " ORDER_GE");
//        	System.out.println((data[index + 1] & 0xFF) + " char");
//        }
        
        // Graphics Escape - treat next byte as character
        byte character = data[index + 1];
        int currentPos = buffer.getCursorPosition();
        
        buffer.setEbcdicCharacter(currentPos, character);

        currentPos++;
        buffer.setCursorPosition(Math.min((currentPos<buffer.getBufferSize()?currentPos:0), buffer.getBufferSize() - 1));
        
        return index + 2;
    }
    
    public int processStructuredField(byte[] data, int index, int length) {
        if (index + 2 >= length) {
			return index + 1;
		}
        
//        if(debug) {
//			System.out.println((data[index] & 0xFF) + " ORDER_STRUCT_FIELD");
//		}
        
        // Skip structured field for now
        int fieldLength = ((data[index] & 0xFF) << 8) | (data[index + 1] & 0xFF);
        return index + fieldLength;
    }
    
    public int processCharacter(byte[] data, int index, int length) {
        if (index >= length) {
			return index;
		}
        
//        if(debug) {
//			System.out.println((data[index] & 0xFF) + " '" + Tn3270Conversions.ebcdicToAscii(data[index]) + "'");
//		}
        
        byte b = data[index];
        
        int currentPos = buffer.getCursorPosition();
        
        buffer.setEbcdicCharacter(currentPos, b);
        
        currentPos++;
        buffer.setCursorPosition(Math.min((currentPos<buffer.getBufferSize()?currentPos:0), buffer.getBufferSize() - 1));
        
        return index + 1;
    }
    
    private int decodeAddress(byte byte1, byte byte2) {
        // 3270 address decoding
    	
        int addr1 = (byte1 & 0x3F);
        int addr2 = (byte2 & 0x3F);
        
//        if(debug) {
//    		System.out.println((byte1 & 0xFF) + " high");
//    		System.out.println((byte2 & 0xFF) + " low");
//    		System.out.println(((addr1 << 6) | addr2) + " final pos");
//    	}
        
        return (addr1 << 6) | addr2;
    }
    
    public boolean isRunning() {
        return running;
    }
}
