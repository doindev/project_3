package org.me.ibm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;

public class SimpleTerminalUI extends JFrame implements ScreenUpdateListener {
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
                    terminal.type(text);
                    inputField.setText("");
                    try {
                        terminal.sendEnter();
                    } catch (IOException ex) {
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
                            terminal.sendPF(1);
                            break;
                        case KeyEvent.VK_F2:
                            terminal.sendPF(2);
                            break;
                        case KeyEvent.VK_F3:
                            terminal.sendPF(3);
                            break;
                        case KeyEvent.VK_F4:
                            terminal.sendPF(4);
                            break;
                        case KeyEvent.VK_F5:
                            terminal.sendPF(5);
                            break;
                        case KeyEvent.VK_F6:
                            terminal.sendPF(6);
                            break;
                        case KeyEvent.VK_F7:
                            terminal.sendPF(7);
                            break;
                        case KeyEvent.VK_F8:
                            terminal.sendPF(8);
                            break;
                        case KeyEvent.VK_F9:
                            terminal.sendPF(9);
                            break;
                        case KeyEvent.VK_F10:
                            terminal.sendPF(10);
                            break;
                        case KeyEvent.VK_F11:
                            terminal.sendPF(11);
                            break;
                        case KeyEvent.VK_F12:
                            terminal.sendPF(12);
                            break;
                        case KeyEvent.VK_TAB:
                            terminal.tab();
                            e.consume();
                            break;
                    }
                } catch (IOException ex) {
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
                terminal.sendClear();
            } catch (IOException ex) {
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
                terminal.sendPF(pfNumber);
            } catch (IOException ex) {
                showError("Error sending " + name + ": " + ex.getMessage());
            }
        });
        return item;
    }
    
    private JMenuItem createPAMenuItem(String name, int paNumber) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            try {
                terminal.sendPA(paNumber);
            } catch (IOException ex) {
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
            screenArea.setText(terminal.getScreenText());
            
            // Update cursor position
            BufferPosition cursor = terminal.getBuffer().getCursorBufferPosition();
            cursorLabel.setText(String.format("Cursor: %d,%d (pos %d)", 
                cursor.getRow(), cursor.getCol(), cursor.getPosition()));
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SimpleTerminalUI().setVisible(true);
        });
    }
}