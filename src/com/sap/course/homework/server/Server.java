package com.sap.course.homework.server;

import com.sap.course.homework.domain.User;
import com.sap.course.homework.util.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server Side
 *
 * @author borislav.draganov
 */

public class Server implements Runnable {
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;

    // Container for connected users
    private Map<User, SocketChannel> connectedUsers;
    private Map<SocketChannel, User> connectedChannels;

    // Valid users
    private Map<String, String> registeredUsers;

    private Map<SelectionKey, FileChannelWrapper> fileTransferChannels;

    public Server(int port) {
        connectedUsers = new ConcurrentHashMap<>();
        connectedChannels = new ConcurrentHashMap<>();
        registeredUsers = new ConcurrentHashMap<>();
        fileTransferChannels = new ConcurrentHashMap<>();

        try {
            initServer(port);
            loadUsers();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start Server
     *
     * @param port - The port the server will listen on
     * @throws IOException
     */
    private void initServer(int port) throws IOException {
        System.out.println("Starting Server");
        // Open Server Socket Channel
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.configureBlocking(false);

        // Register Selector
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
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

                    // New Client
                    if (key.isAcceptable()) {
                        System.out.println("New Client Accepted");
                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                        serverSocketChannel.configureBlocking(false);

                        SocketChannel socketChannel = serverSocketChannel.accept();
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);
                    }
                    // Client has sent data
                    else if (key.isReadable()) {
                        handleMsg(key);
                    }

                    keyIterator.remove();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Check a message if its a system message with user credentials
     *
     * @param data - The message to be checked
     * @return - User object if credentials were given or null if not credentials
     * @throws IOException
     */
    private User checkMsgForCredentials(String data) throws IOException {
        if (data.matches(".*:.*")) {
            String[] credentials = data.split(":");
            return new User(credentials[0], credentials[1]);
        }

        return null;
    }

    /**
     * Handle a received message on a channel
     *
     * @param key - The SelectionKey
     * @throws IOException
     */
    private void handleMsg(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Download file
        if (fileTransferChannels.containsKey(key)) {
            FileChannelWrapper fileChannelWrapper = fileTransferChannels.get(key);

            ChannelHandler.saveFile(key, fileChannelWrapper);

            if (!fileChannelWrapper.getFileChannel().isOpen()) {
                fileTransferChannels.remove(key);
            }
        } else {
            // Read data
            String msg = ChannelHandler.readMsg(socketChannel);
            System.out.println("Received data : " + msg);

            // Check if the data was user credentials
            User user = checkMsgForCredentials(msg);
            if (user != null) {
                System.out.println("Handling new user : " + user);

                // Check if valid login
                if (isValidLogin(user)) {
                    connectedUsers.put(user, socketChannel);
                    connectedChannels.put(socketChannel, user);
                }
                // If not - reject the socket
                else {
                    ChannelHandler.sendCommand(socketChannel, SystemCommand.INVALID_USER);
                }
            }
            // Check if the message is a system command
            else if (SystemCommand.isCommand(msg)) {
                // Disconnect the user
                if (msg.contains(SystemCommand.DISCONNECT.getCommand())) {
                    User userToDisconnect = connectedChannels.get(socketChannel);

                    connectedChannels.remove(socketChannel);
                    connectedUsers.remove(userToDisconnect);
                }
                // Receive file
                else if (msg.contains(SystemCommand.FILE_UPLOAD.getCommand())) {
                    String[] data = msg.split("-");

                    File file = new File(Constants.SERVER_FILE_DIR + data[1]);

                    file.createNewFile();

                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    FileChannel fileChannel = fileOutputStream.getChannel();

                    System.out.println("Receiving file");
                    fileTransferChannels.put(key, new FileChannelWrapper(Integer.parseInt(data[2]), fileChannel));
                }
                // Send file
                else if (msg.contains(SystemCommand.FILE_DOWNLOAD.getCommand())) {
                    String[] data = msg.split("-");

                    File file = new File(Constants.SERVER_FILE_DIR + data[1]);

                    if (file.exists()) {
                        System.out.println("Server sending file");
                        ChannelHandler.sendFile(file, socketChannel);
                    } else {
                        ChannelHandler.sendCommand(socketChannel, SystemCommand.FILE_NOT_FOUND);
                        socketChannel.close();
                        key.cancel();
                    }
                }
            }
            // Check for user commands
            else if (UserCommand.isCommand(msg)) {
                File dir = new File(Constants.SERVER_FILE_DIR);
                File[] files = dir.listFiles();

                for (File file : files) {
                    ChannelHandler.sendMsg(socketChannel, file.getName());
                }
            }
            // If not - forward the message
            else {
                // Get the user to exclude
                User currentUser = connectedChannels.get(socketChannel);

                forwardMsg(msg, currentUser);
            }
        }
    }

    /**
     * Check if the user is allowed to be logged in
     *
     * @param user - The user requesting login
     * @return - true if the user is permitted, false otherwise
     */
    private boolean isValidLogin(User user) throws IOException {
        // Register user
        if (!registeredUsers.containsKey(user.getUsername())) {
            String password = user.getPassword();
            if (password == null || password == "") { return false; } // Can't register without a password

            registeredUsers.put(user.getUsername(), password);
            registerUser(user);

            return true;
        }

        // First check if the credentials are valid
        String pass = registeredUsers.get(user.getUsername());
        if (!pass.equals(user.getPassword())) {
            return false;
        }

        // Then check if the user is not already logged in
        Set<User> users = connectedUsers.keySet();
        String username = user.getUsername();

        // Deny access if the username is in use
        for (User connectedUser : users) {
            if (connectedUser.getUsername().equals(username)) {
                return false;
            }
        }

        // All clear
        return true;
    }

    /**
     * Forward the message to everybody except the selected user
     *
     * @param msg - Message to send
     * @param excludedUser - The user to exclude
     * @throws IOException
     */
    private void forwardMsg(String msg, User excludedUser) throws IOException {
        //System.out.println("forwardMsg");
        Set<User> keySet = connectedUsers.keySet();
        int count = 0;

        // Forward the message to everyone else
        for (User user : keySet) {
            if (user.equals(excludedUser)) {
                continue;
            }

            SocketChannel socketChannel = connectedUsers.get(user);

            ChannelHandler.sendMsg(socketChannel, excludedUser.getUsername() + ": " + msg);
            count++;
        }

        // Send a status to the user
        SocketChannel socketChannel = connectedUsers.get(excludedUser);
        ChannelHandler.sendMsg(socketChannel, String.format(Constants.MESSAGE_SENT_TO_USERS, count));
    }

    /**
     * Load the list of valid users in the memory
     * The list is in format user:pass
     */
    private void loadUsers() throws IOException {
        File usersFile = new File("users.txt");
        if (!usersFile.exists()) {
            usersFile.createNewFile();
        }

        FileInputStream is = new FileInputStream(usersFile);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

        for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
            String[] pair = line.split(":");

            registeredUsers.put(pair[0], pair[1]);
        }
    }

    /**
     * Register a new user
     *
     * @param user - The to be registered
     * @throws IOException
     */
    private void registerUser(User user) throws IOException {
        File usersFile = new File("users.txt");
        if (!usersFile.exists()) {
            usersFile.createNewFile();
        }

        FileOutputStream is = new FileOutputStream(usersFile, true);
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(is));

        bufferedWriter.write(user.getUsername() + ":" + user.getPassword());
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }
}