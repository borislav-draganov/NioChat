package com.sap.course.homework.util;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * WindowListener implementation for the Client
 *
 * @author borislav.draganov
 */

public class WinListener implements WindowListener {
    // Client's Socket Channel
    private SocketChannel socketChannel;

    public WinListener(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public void windowOpened(WindowEvent e) {

    }

    @Override
    public void windowClosing(WindowEvent e) {
        try {
            ChannelHandler.sendCommand(socketChannel, SystemCommand.DISCONNECT);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {

    }

    @Override
    public void windowDeiconified(WindowEvent e) {

    }

    @Override
    public void windowActivated(WindowEvent e) {

    }

    @Override
    public void windowDeactivated(WindowEvent e) {

    }
}