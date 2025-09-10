package org.me.ibm;

import java.io.IOException;

public class Tn3270Example {
    
    public static void main(String[] args) {
        Tn3270 terminal = new Tn3270();
        
        try {
            // Connect to mainframe
            System.out.println("Connecting to mainframe...");
            terminal.connect("your-mainframe-host.com", 23);
            
            // Wait for initial screen to load
            terminal.waitForConnection();
            terminal.waitForScreenUpdate();
            
            System.out.println("Connected! Current screen:");
            System.out.println(terminal.getScreenText());
            
            // Example: Login sequence
            // Navigate to userid field and enter userid
            terminal.home(); // Go to first unprotected field
            terminal.type("MYUSERID");
            
            // Tab to password field and enter password
            terminal.tab();
            terminal.type("MYPASSWORD");
            
            // Press Enter to submit
            terminal.sendEnter();
            
            // Wait for response
            terminal.waitForScreenUpdate();
            System.out.println("After login:");
            System.out.println(terminal.getScreenText());
            
            // Example: Navigate through application
            terminal.sendPF(3); // Press PF3
            terminal.waitForScreenUpdate();
            
            System.out.println("After PF3:");
            System.out.println(terminal.getScreenText());
            
            // Example: Type at specific position
            terminal.type(10, 20, "Hello World");
            terminal.sendEnter();
            terminal.waitForScreenUpdate();
            
            // Get specific row text
            String row5 = terminal.getScreenText(5);
            System.out.println("Row 5: " + row5);
            
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Interrupted: " + e.getMessage());
        } finally {
            try {
                terminal.disconnect();
                System.out.println("Disconnected.");
            } catch (IOException e) {
                System.err.println("Error disconnecting: " + e.getMessage());
            }
        }
    }
    
    public static void simpleScreenListener() {
        Tn3270 terminal = new Tn3270();
        
        // Add a screen update listener
        terminal.addScreenUpdateListener(new ScreenUpdateListener() {
            @Override
            public void onScreenUpdate() {
                System.out.println("Screen updated!");
                System.out.println("Current screen content:");
                System.out.println(terminal.getScreenText());
                System.out.println("Cursor position: " + terminal.getBuffer().getCursorPosition());
            }
        });
        
        try {
            terminal.connect("mainframe.example.com", 23);
            
            // Let it run for a while to see updates
            Thread.sleep(10000);
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                terminal.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void fieldNavigationExample() {
        Tn3270 terminal = new Tn3270();
        
        try {
            terminal.connect();
            terminal.waitForConnection();
            
            // Navigate through fields using tab
            terminal.home(); // Go to first field
            terminal.type("Field 1 data");
            
            terminal.tab(); // Next field
            terminal.type("Field 2 data");
            
            terminal.tab(); // Next field
            terminal.type("Field 3 data");
            
            // Submit the form
            terminal.sendEnter();
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                terminal.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}