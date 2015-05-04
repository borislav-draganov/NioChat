package com.sap.course.homework.client;

import com.sap.course.homework.domain.User;
import com.sap.course.homework.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Client Side
 *
 * @author borislav.draganov
 */

public class Client extends JFrame implements Runnable, ActionListener, KeyEventDispatcher {
    private String address;

    // Elements needed when transmitting files
    private boolean connectedToServer;
    private boolean downloadRequest;
    private FileChannelWrapper fileChannelWrapper;
    private SelectionKey transferSelectionKey;
    private String fileName;

    // NIO Elements
    private SocketChannel socketChannel;
    private Selector selector;

    // GUI Elements
    private JScrollPane chatScrollPane;
    private JTextPane chatTextPane;
    private JTextField messageTextField;
    private JButton sendMsgBtn, sendFileBtn;

    // Used for file transfer
    private SocketChannel fileTransferChannel;

    // Window Listener
    private WinListener winListener;

    public Client(String address) {
        this.address = address;
        connectedToServer = false;
        downloadRequest = false;
        System.out.println("Starting Client");

        initGUI();

        try {
            initClient(address, Constants.PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize the client - connect to a server
     *
     * @param hostname - The server hostname
     * @param port - The server port
     * @throws IOException
     */
    private void initClient(String hostname, int port) throws IOException {
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        selector = Selector.open();

        socketChannel.connect(new InetSocketAddress(hostname, port));
        socketChannel.register(selector, SelectionKey.OP_CONNECT);

        // Register Window Listener - to disconnect the client when the window is closed
        winListener = new WinListener(socketChannel);
        this.addWindowListener(winListener);
    }

    /**
     * Initialize the client GUI
     */
    private void initGUI() {
        // Main Frame
        this.setLayout(new GridBagLayout());
        this.setTitle("NIO Client");

        GridBagConstraints constraints = new GridBagConstraints();

        // Chat Text Pane
        chatTextPane = new JTextPane();
        chatTextPane.setPreferredSize(new Dimension(300, 200));
        chatTextPane.setEditable(false);
        constraints.ipady = 0;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        chatScrollPane = new JScrollPane(chatTextPane);
        this.add(chatScrollPane, constraints);

        // Message Text Field
        messageTextField = new JTextField();
        messageTextField.setPreferredSize(new Dimension(200, 20));
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        this.add(messageTextField, constraints);

        // Send Message Button
        sendMsgBtn = new JButton("Send");
        constraints.gridx = 1;
        this.add(sendMsgBtn, constraints);
        sendMsgBtn.addActionListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);

        // Send File Button
        sendFileBtn = new JButton("Send File");
        constraints.gridx = 2;
        this.add(sendFileBtn, constraints);
        sendFileBtn.addActionListener(this);

        this.pack();
        this.setMinimumSize(this.getSize());
        this.setVisible(true);
    }

    /**
     * Send the credentials to the server
     *
     * @throws IOException
     */
    private void sendCredentials() throws IOException {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel labels = new JPanel(new GridLayout(0, 1, 2, 2));
        labels.add(new JLabel("User", SwingConstants.RIGHT));
        labels.add(new JLabel("Pass", SwingConstants.RIGHT));
        panel.add(labels, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField usernameField = new JTextField();
        controls.add(usernameField);
        JPasswordField passwordField = new JPasswordField();
        controls.add(passwordField);
        panel.add(controls, BorderLayout.CENTER);

        String username = "";
        String password = "";

        while (username == null || username.equals("") ||
                password == null || password.equals("")) {
            JOptionPane.showConfirmDialog(this, panel, "Enter credentials", JOptionPane.OK_CANCEL_OPTION);

            username = usernameField.getText();
            password = new String(passwordField.getPassword());
        }

        User user = new User(username, password);

        ChannelHandler.sendMsg(socketChannel, user.toString());
    }

    /**
     * Send the typed message to the server
     *
     * @throws IOException
     */
    private void sendMsg() throws IOException {
        String msg = messageTextField.getText();
        messageTextField.setText("");

        msg = msg.trim();
        if (ChannelHandler.isInvalidMsg(msg)) {
            return;
        }

        // Check for download command
        if (UserCommand.isCommand(msg)) {
            if (msg.contains(UserCommand.FILE_DOWNLOAD.getCommand())) {
                fileName = msg.split(" ")[1];
                openFileTransferChannel();
                downloadRequest = true;
            } else {
                ChannelHandler.sendMsg(socketChannel, msg);
            }
        } else {
            ChannelHandler.sendMsg(socketChannel, msg);
        }
    }

    /**
     * Open an additional channel for transmitting files
     *
     * @throws IOException
     */
    private void openFileTransferChannel() throws IOException {
        fileTransferChannel = SocketChannel.open();
        fileTransferChannel.configureBlocking(false);

        fileTransferChannel.connect(new InetSocketAddress(address, Constants.PORT));

        fileTransferChannel.register(selector, SelectionKey.OP_CONNECT);
    }

    /**
     * Select a file and send it on the file transfer channel
     *
     * @throws IOException
     */
    private void sendFile() throws IOException {
        JFileChooser fileChooser = new JFileChooser();
        int returnVal = fileChooser.showOpenDialog(this);

        if(returnVal == JFileChooser.APPROVE_OPTION) {
            ChannelHandler.sendFile(fileChooser.getSelectedFile(), fileTransferChannel);
        }
    }

    /**
     * Send a request to the server for a file
     *
     * @throws IOException
     */
    private void requestFile() throws IOException {
        ChannelHandler.sendMsg(fileTransferChannel, SystemCommand.FILE_DOWNLOAD.getCommand() + "-" + fileName);
        fileTransferChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Get ready channels
                int readyChannels = selector.select();
                if (readyChannels == 0) { continue; }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                // Handle Events
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    // Ready to finish connection
                    if (key.isConnectable()) {
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        socketChannel.finishConnect();

                        // File Transfer Channel
                        if (connectedToServer == true) {
                            if (downloadRequest) {
                                requestFile();
                                downloadRequest = false;
                            } else {
                                sendFile();
                            }
                        }
                        // Main Chat Channel
                        else {
                            socketChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);

                            // Login to the server
                            sendCredentials();
                            connectedToServer = true;
                        }
                    }
                    // Client has sent data
                    else if (key.isReadable()) {
                        // Download file
                        if (key == transferSelectionKey) {
                            ChannelHandler.saveFile(key, fileChannelWrapper);
                        } else {
                            // Read data
                            String data = ChannelHandler.readMsg((SocketChannel) key.channel());
//                            System.out.println("Client received: " + data);

                            if (SystemCommand.isCommand(data)) {
                                // The server rejected the credentials
                                if (data.contains(SystemCommand.INVALID_USER.getCommand())) {
                                    JOptionPane.showMessageDialog(this, "Invalid user credentials", "Error", JOptionPane.ERROR_MESSAGE);
                                    this.dispose();
                                }
                                // The server is sending a file
                                else if (data.contains(SystemCommand.FILE_UPLOAD.getCommand())) {
                                    String[] fileData = data.split("-");

                                    File file = new File(fileData[1]);

                                    file.createNewFile();

                                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                                    FileChannel fileChannel = fileOutputStream.getChannel();
                                    fileChannelWrapper = new FileChannelWrapper(Long.parseLong(fileData[2]), fileChannel);

                                    transferSelectionKey = key;
                                }
                                // Server did not find requested file - close the file channel
                                else if (data.contains(SystemCommand.FILE_NOT_FOUND.getCommand())) {
                                    chatTextPane.setText(chatTextPane.getText() + "File not found" + "\n");
                                    fileTransferChannel.close();
                                    key.cancel();
                                    transferSelectionKey = null;
                                }
                            } else {
                                chatTextPane.setText(chatTextPane.getText() + data + "\n");
                            }
                        }
                    }

                    keyIterator.remove();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == sendMsgBtn) {
            try {
                sendMsg();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } else if (e.getSource() == sendFileBtn) {
            try {
                openFileTransferChannel();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            try {
                sendMsg();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        return false;
    }
}