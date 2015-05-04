package com.sap.course.homework.util;

/**
 * The set of System Commands
 *
 * @author borislav.draganov
 */

public enum SystemCommand {
    INVALID_USER("SYSTEM_COMMAND_INVALID_USER"),
    FILE_UPLOAD("SYSTEM_COMMAND_FILE_UPLOAD"),
    FILE_DOWNLOAD("SYSTEM_COMMAND_FILE_DOWNLOAD"),
    FILE_NOT_FOUND("SYSTEM_COMMAND_FILE_NOT_FOUND"),
    DISCONNECT("SYSTEM_COMMAND_DISCONNECT");

    private String command;

    SystemCommand(String command) {
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
        for (SystemCommand command : SystemCommand.values()) {
            if (str.contains(command.getCommand())) {
                return true;
            }
        }

        return false;
    }
}
