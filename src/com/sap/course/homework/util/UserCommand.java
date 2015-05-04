package com.sap.course.homework.util;

/**
 * The set of User Commands
 *
 * @author borislav.draganov
 */

public enum UserCommand {
    FILE_DOWNLOAD("/download"),
    FILE_LIST("/fileList");

    private String command;

    UserCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    /**
     * Check a string if it's one of the commands
     *
     * @param str - The string to be checked
     * @return - true if the given string is a system command, false otherwise
     */
    public static boolean isCommand(String str) {
        for (UserCommand command : UserCommand.values()) {
            if (str.contains(command.getCommand())) {
                return true;
            }
        }

        return false;
    }
}
