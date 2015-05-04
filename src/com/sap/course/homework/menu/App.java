package com.sap.course.homework.menu;

import com.sap.course.homework.client.Client;
import com.sap.course.homework.server.Server;
import com.sap.course.homework.util.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Main Class
 *
 * @author borislav.draganov
 */

public class App extends JFrame implements ActionListener {
    private JButton startServerBtn;
    private JButton startClientBtn;

    public App() { }

    public static void main(String[] args) {
        boolean commandLineArgs = false;
        App app = new App();

        // Check for command-line arguments
        for (String arg : args) {
            commandLineArgs = true;

            if (arg.equals("-server")) {
                app.startServer();
            } else if (arg.equals("-client")) {
                app.startClient();
            }
        }

        if (!commandLineArgs) {
            app.initGUI();
        }
    }

    /**
     * Initialize and show the GUI
     */
    public void initGUI() {// Close on exit
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // Set layout
        this.setLayout(new FlowLayout());

        // Initialize buttons and add them to the content pane
        this.startServerBtn = new JButton("Start Server");
        this.startClientBtn = new JButton("Start Client");

        this.add(startServerBtn);
        this.add(startClientBtn);

        // Register Listeners
        startServerBtn.addActionListener(this);
        startClientBtn.addActionListener(this);

        // Display frame
        this.setTitle("NIO Chat");
        this.pack();
        this.setVisible(true);
    }

    /**
     * Start a new server
     */
    public void startServer() {
        new Thread(new Server(Constants.PORT)).start();
    }

    /**
     * Start a new client
     */
    public void startClient() {
//        String address = JOptionPane.showInputDialog(this, "Enter the server address (IP address)", "Select server", JOptionPane.PLAIN_MESSAGE);
        String address = "localhost";
        new Thread(new Client(address)).start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == startServerBtn) {
            startServer();
        } else if (e.getSource() == startClientBtn) {
            startClient();
        }

        //this.dispose();
    }
}