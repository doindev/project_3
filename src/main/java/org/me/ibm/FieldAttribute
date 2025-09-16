package org.me.ibm;

public class FieldAttribute {
	/*
	 * from right to left
	 * bit 0 	modified
	 * bit 1 	reserved
	 * bit 2-3 	normal/bright/non-display
	 * bit 4 	numeric only/alphanumeric
	 * bit 5 	protected/unprotected
	 * bit 6-7 	control bits
	 */
	
	private static final byte MODIFIED_MASK = 0x01;
	private static final byte NUMERIC_MASK = 0x10;
    private static final byte PROTECTED_MASK = 0x20;
    private static final byte SKIP_PROTECTED_MASK = 0x30;// protected + numeric
    private static final byte DISPLAY_MASK = 0x0C;
    
    private FieldType type;
    private FieldIntensity intensity;
    private FieldColor color;
    private FieldHighlighting highlighting;
    private boolean modified;
    private boolean numeric;
    private boolean skipProtected;
    private boolean rightJustify;
    private boolean visible;

    private byte attribute;
    
    
    public FieldAttribute(byte attribute) {
        parseAttribute(attribute);
    }
    
    public FieldAttribute() {
        this.type = FieldType.UNPROTECTED;
        this.intensity = FieldIntensity.NORMAL;
        this.color = FieldColor.DEFAULT;
        this.highlighting = FieldHighlighting.NORMAL;
        this.modified = false;
        this.numeric = false;
        this.skipProtected = false;
        this.rightJustify = false;
        this.visible = true;
    }
    
    private void parseAttribute(byte attribute) {
    	this.attribute = attribute;
        boolean isProtected = (attribute & PROTECTED_MASK) != 0;
        this.numeric = (attribute & NUMERIC_MASK) != 0;
        this.skipProtected = (attribute & SKIP_PROTECTED_MASK) == SKIP_PROTECTED_MASK;
        
        if (skipProtected) {
            this.type = FieldType.SKIP_PROTECTED;
        } else if (isProtected) {
            this.type = FieldType.PROTECTED;
        } else {
            this.type = FieldType.UNPROTECTED;
        }
        
        int displayBits = (attribute & DISPLAY_MASK) >> 2;
        switch (displayBits) {
            case 0:
                this.intensity = FieldIntensity.NORMAL;
                this.visible = true;
                break;
            case 1:
                this.intensity = FieldIntensity.HIGH;
                this.visible = true;
                break;
            case 2:
                this.intensity = FieldIntensity.ZERO;
                this.visible = false;
                break;
            case 3:
                this.intensity = FieldIntensity.ZERO;
                this.visible = false;
                break;
        }
        
        this.modified = (attribute & MODIFIED_MASK) != 0;
        this.color = FieldColor.DEFAULT;
        this.highlighting = FieldHighlighting.NORMAL;
        this.rightJustify = false;
    }
    
    public FieldType type() {
        return type;
    }
    
    public FieldAttribute type(FieldType type) {
        this.type = type;
        return this;
    }
    
    public FieldIntensity intensity() {
        return intensity;
    }
    
    public FieldAttribute intensity(FieldIntensity intensity) {
        this.intensity = intensity;
        return this;
    }
    
    public FieldColor color() {
        return color;
    }
    
    public FieldAttribute color(FieldColor color) {
        this.color = color;
        return this;
    }
    
    public FieldHighlighting highlighting() {
        return highlighting;
    }
    
    public FieldAttribute highlighting(FieldHighlighting highlighting) {
        this.highlighting = highlighting;
        return this;
    }
    
    public boolean canInput() {
        return type == FieldType.UNPROTECTED;
    }
    
    public boolean isProtected() {
		return type == FieldType.PROTECTED || type == FieldType.SKIP_PROTECTED;
	}
    
    public boolean isSkipProtected() {
        return skipProtected || type == FieldType.SKIP_PROTECTED;
    }
    
    public boolean isModified() {
        return modified;
    }
    
    public boolean isNumeric() {
        return numeric;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public boolean isRightJustify() {
        return rightJustify;
    }
    
    public FieldAttribute modified(boolean b) {
        this.modified = b;
        return this;
    }
    
    public FieldAttribute numeric(boolean b) {
        this.numeric = b;
        return this;
    }
    
    public FieldAttribute autoSkip(boolean b) {
        this.skipProtected = b;
        return this;
    }
    
    public FieldAttribute rightJustify(boolean b) {
        this.rightJustify = b;
        return this;
    }
    
    public FieldAttribute visible(boolean b) {
        this.visible = b;
        return this;
    }
    
    public byte toAttributeByte() {
        // Start with the original attribute byte
        byte result = (byte) (attribute & ~(PROTECTED_MASK | NUMERIC_MASK | DISPLAY_MASK | MODIFIED_MASK));

        // Set protected/skip-protected bits
        if (type == FieldType.SKIP_PROTECTED) {
            result |= SKIP_PROTECTED_MASK;
        } else if (type == FieldType.PROTECTED) {
            result |= PROTECTED_MASK;
        }

        // Set numeric bit
        if (numeric) {
            result |= NUMERIC_MASK;
        }

        // Set display bits
        if (intensity == FieldIntensity.HIGH) {
            result |= 0x04;
        } else if (!visible || intensity == FieldIntensity.ZERO) {
            result |= 0x0C;
        }

        // Set modified bit
        if (modified) {
            result |= MODIFIED_MASK;
        }

        return result;
    }
    
    @Override
    public String toString() {
		return "FieldAttribute [type=" + type + ", intensity=" + intensity + ", color=" + color + ", highlighting="
				+ highlighting + ", modified=" + modified + ", numeric=" + numeric + ", autoSkip=" + skipProtected
				+ ", rightJustify=" + rightJustify + ", visible=" + visible + " attribute=" + String.format("0x%02X", this.toAttributeByte()) + ", byte=" + String.format("0x%02X", this.attribute) + "]";
	}
    
    public static void main(String[] args) {
    	FieldAttribute fa = new FieldAttribute((byte)0x60);
    	System.out.println(fa);
    }
}
