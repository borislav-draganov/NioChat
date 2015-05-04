package com.sap.course.homework.util;

import javax.swing.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Handler for Channel Sockets
 *
 * @author borislav.draganov
 */

public class ChannelHandler {
    /**
     * Send a message on a socket channel
     *
     * @param socketChannel - The target socket channel on which to send the message
     * @param msg - The message to send
     * @throws IOException
     */
    public static void sendMsg(SocketChannel socketChannel, String msg) throws IOException {
        msg += "\n";
        System.out.println("Sending msg : " + msg);
        ByteBuffer byteBuffer = ByteBuffer.wrap(msg.getBytes(Charset.forName(Constants.UTF_ENCODING)));

        socketChannel.write(byteBuffer);
    }

    /**
     * Read a message from a selection key
     *
     * @param socketChannel - The Socket Channel receiving the message
     * @return - The message that's on the channel
     * @throws IOException
     */
    public static String readMsg(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Constants.SINGLE_BYTE);

        StringBuilder stringBuilder = new StringBuilder();

        // Read byte by byte until \n is found
        for (int i = 0; i < Constants.BUFFER_SIZE; i++) {
            socketChannel.read(byteBuffer);
            byteBuffer.flip();

            String byteStr = new String(byteBuffer.array(), Charset.forName(Constants.UTF_ENCODING));

            if (byteStr.equals("\n")) {
                break;
            } else {
                stringBuilder.append(byteStr);
            }
        }

        return stringBuilder.toString();
    }

    /**
     * Check a message if it's invalid
     *
     * @param msg - The string to be checked
     * @return - true if the message is invalid, false otherwise
     */
    public static boolean isInvalidMsg(String msg) {
        return msg.equals("") || SystemCommand.isCommand(msg) || msg.contains(":") || msg.contains("-");
    }

    /**
     * Send a system command on the socket channel
     *
     * @param socketChannel - The socket channel on which to send the command
     * @param command - The command to be send
     * @throws IOException
     */
    public static void sendCommand(SocketChannel socketChannel, SystemCommand command) throws IOException {
        sendMsg(socketChannel, command.getCommand());
    }

    /**
     * Send a file on a given channel
     *
     * @param selectedFile - The file to be transmitted
     * @param fileTransferChannel - The channel on which to send the file
     * @throws IOException
     */
    public static void sendFile(File selectedFile, SocketChannel fileTransferChannel) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(selectedFile);
        FileChannel fileChannel = fileInputStream.getChannel();

        // Send the upload command + file name + size
        sendMsg(fileTransferChannel, SystemCommand.FILE_UPLOAD.getCommand() + "-" + selectedFile.getName() + "-" + fileChannel.size());

        // Transfer the data
        long size = fileChannel.size();
        long position = 0;
        while (position < size) {
            position += fileChannel.transferTo(position, Constants.FILE_FRAGMENT_SIZE, fileTransferChannel);
        }

        // Close the channel
        fileTransferChannel.close();
    }

    /**
     * Save a file with the received bytes from a socket channel
     *
     * @param key - The selection key with the data
     * @param fileChannelWrapper - The FileChannel with the file's size
     * @throws IOException
     */
    public static void saveFile(SelectionKey key, FileChannelWrapper fileChannelWrapper) throws IOException {
        FileChannel fileChannel = fileChannelWrapper.getFileChannel();
        SocketChannel socketChannel = (SocketChannel) key.channel();

        long readBytes = fileChannel.transferFrom(socketChannel, fileChannelWrapper.getPosition(), Constants.FILE_FRAGMENT_SIZE);
        fileChannelWrapper.incrementPosition(readBytes);

        // Close the file channel if all bytes are given
        if (fileChannelWrapper.getPosition() >= fileChannelWrapper.getSize()) {
            fileChannel.force(false);
            key.cancel();

            fileChannel.close();
            socketChannel.close();
        }
    }
}