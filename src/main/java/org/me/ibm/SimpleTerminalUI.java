package org.me.ibm;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class SimpleTerminalUI extends JFrame implements ScreenUpdateListener {
	private static final long serialVersionUID = 1L;
	private final Tn3270 terminal;
    private final JTextArea screenArea;
    private final JTextField inputField;
    private final JLabel statusLabel;
    private final JLabel cursorLabel;
    
    public SimpleTerminalUI() {
        this.terminal = new Tn3270();
        this.screenArea = new JTextArea(24, 80);
        this.inputField = new JTextField(40);
        this.statusLabel = new JLabel("Disconnected");
        this.cursorLabel = new JLabel("Cursor: 0,0");
        
        setupUI();
        terminal.addScreenUpdateListener(this);
    }
    
    private void setupUI() {
        setTitle("TN3270 Terminal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Screen area
        screenArea.setFont(new Font("Courier New", Font.PLAIN, 12));
        screenArea.setEditable(false);
        screenArea.setBackground(Color.BLACK);
        screenArea.setForeground(Color.GREEN);
        JScrollPane scrollPane = new JScrollPane(screenArea);
        add(scrollPane, BorderLayout.CENTER);
        
        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = inputField.getText();
                if (!text.isEmpty()) {
                	try {
                		terminal.screen().putString(text);
                	} catch(Exception ex) {
						showError("Error sending data: " + ex.getMessage());
					}
                    inputField.setText("");
                    try {
                        terminal.screen().enter();
                    } catch (IOException | InterruptedException | TimeoutException ex) {
                        showError("Error sending data: " + ex.getMessage());
                    }
                }
            }
        });
        
        // Add key listener for function keys
        inputField.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                try {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_F1:
                            terminal.screen().pf1();
                            break;
                        case KeyEvent.VK_F2:
                            terminal.screen().pf2();
                            break;
                        case KeyEvent.VK_F3:
                        	terminal.screen().pf3();
                            break;
                        case KeyEvent.VK_F4:
                        	terminal.screen().pf4();
                            break;
                        case KeyEvent.VK_F5:
                        	terminal.screen().pf5();
                            break;
                        case KeyEvent.VK_F6:
                        	terminal.screen().pf6();
                            break;
                        case KeyEvent.VK_F7:
                        	terminal.screen().pf7();
                            break;
                        case KeyEvent.VK_F8:
                        	terminal.screen().pf8();
                            break;
                        case KeyEvent.VK_F9:
                        	terminal.screen().pf9();
                            break;
                        case KeyEvent.VK_F10:
                        	terminal.screen().pf10();
                            break;
                        case KeyEvent.VK_F11:
                        	terminal.screen().pf11();
                            break;
                        case KeyEvent.VK_F12:
                        	terminal.screen().pf12();
                            break;
                        case KeyEvent.VK_TAB:
                            terminal.screen().tab();
                            e.consume();
                            break;
                    }
                } catch (IOException | InterruptedException | TimeoutException ex) {
                    showError("Error sending key: " + ex.getMessage());
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {}
            
            @Override
            public void keyTyped(KeyEvent e) {}
        });
        
        inputPanel.add(new JLabel("Input: "), BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
        
        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createHorizontalStrut(20));
        statusPanel.add(cursorLabel);
        add(statusPanel, BorderLayout.NORTH);
        
        // Menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu connectionMenu = new JMenu("Connection");
        
        JMenuItem connectItem = new JMenuItem("Connect");
        connectItem.addActionListener(e -> connectToHost());
        connectionMenu.add(connectItem);
        
        JMenuItem disconnectItem = new JMenuItem("Disconnect");
        disconnectItem.addActionListener(e -> disconnect());
        connectionMenu.add(disconnectItem);
        
        connectionMenu.addSeparator();
        
        JMenuItem clearItem = new JMenuItem("Clear Screen");
        clearItem.addActionListener(e -> {
            try {
                terminal.screen().clear();
            } catch (IOException | InterruptedException | TimeoutException ex) {
                showError("Error clearing screen: " + ex.getMessage());
            }
        });
        connectionMenu.add(clearItem);
        
        menuBar.add(connectionMenu);
        
        JMenu actionMenu = new JMenu("Actions");
        actionMenu.add(createPFMenuItem("PF1", 1));
        actionMenu.add(createPFMenuItem("PF2", 2));
        actionMenu.add(createPFMenuItem("PF3", 3));
        actionMenu.addSeparator();
        actionMenu.add(createPAMenuItem("PA1", 1));
        actionMenu.add(createPAMenuItem("PA2", 2));
        actionMenu.add(createPAMenuItem("PA3", 3));
        
        menuBar.add(actionMenu);
        setJMenuBar(menuBar);
        
        pack();
        setLocationRelativeTo(null);
    }
    
    private JMenuItem createPFMenuItem(String name, int pfNumber) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            try {
            	switch (pfNumber) {
                case 1:
                    terminal.screen().pf1();
                    break;
                case 2:
                    terminal.screen().pf2();
                    break;
                case 3:
                	terminal.screen().pf3();
                    break;
                case 4:
                	terminal.screen().pf4();
                    break;
                case 5:
                	terminal.screen().pf5();
                    break;
                case 6:
                	terminal.screen().pf6();
                    break;
                case 77:
                	terminal.screen().pf7();
                    break;
                case 8:
                	terminal.screen().pf8();
                    break;
                case 9:
                	terminal.screen().pf9();
                    break;
                case 10:
                	terminal.screen().pf10();
                    break;
                case 11:
                	terminal.screen().pf11();
                    break;
                case 12:
                	terminal.screen().pf12();
                    break;
            	}
            } catch (IOException | InterruptedException | TimeoutException ex) {
                showError("Error sending " + name + ": " + ex.getMessage());
            }
        });
        return item;
    }
    
    private JMenuItem createPAMenuItem(String name, int paNumber) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            try {
            	switch (paNumber) {
                case 1:
                    terminal.screen().pa1();
                    break;
                case 2:
                    terminal.screen().pa2();
                    break;
                case 3:
                	terminal.screen().pa3();
                    break;
            	}
            } catch (IOException | InterruptedException | TimeoutException ex) {
                showError("Error sending " + name + ": " + ex.getMessage());
            }
        });
        return item;
    }
    
    private void connectToHost() {
        String host = JOptionPane.showInputDialog(this, "Enter hostname:", "localhost");
        if (host != null && !host.trim().isEmpty()) {
            String portStr = JOptionPane.showInputDialog(this, "Enter port:", "23");
            try {
                int port = Integer.parseInt(portStr);
                terminal.connect(host.trim(), port);
                statusLabel.setText("Connected to " + host + ":" + port);
                inputField.setEnabled(true);
                inputField.requestFocus();
            } catch (NumberFormatException e) {
                showError("Invalid port number");
            } catch (IOException e) {
                showError("Connection failed: " + e.getMessage());
            }
        }
    }
    
    private void disconnect() {
        try {
            terminal.disconnect();
            statusLabel.setText("Disconnected");
            inputField.setEnabled(false);
            screenArea.setText("");
        } catch (IOException e) {
            showError("Error disconnecting: " + e.getMessage());
        }
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    @Override
    public void onScreenUpdate() {
        SwingUtilities.invokeLater(() -> {
            // Update screen content
        	try {
        		screenArea.setText(terminal.screen().getString());
        	} catch (Exception e) {
        		screenArea.setText("Error retrieving screen content: " + e.getMessage());
        	}
            
            // Update cursor position
            BufferPosition cursor = terminal.buffer().getCursorBufferPosition();
            cursorLabel.setText(String.format("Cursor: %d,%d (pos %d)",  cursor.getRow(), cursor.getCol(), cursor.getPosition()));
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SimpleTerminalUI().setVisible(true);
        });
    }
}
