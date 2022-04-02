package com.sachett.samosa.playground.models;

public class RunRequest {
    private String program;
    private String cmdlineArgs;
    private String input;
    private boolean hasInput;
    private boolean endRun;

    public boolean isHasInput() {
        return hasInput;
    }

    public boolean isEndRun() {
        return endRun;
    }

    public RunRequest() {}

    public RunRequest(String program, String cmdlineArgs, String input, boolean hasInput, boolean endRun) {
        this.program = program;
        this.cmdlineArgs = cmdlineArgs;
        this.hasInput = hasInput;
        this.endRun = endRun;
        this.input = input;
    }

    public String getProgram() {
        return program;
    }

    public String getCmdlineArgs() {
        return cmdlineArgs;
    }

    public String getInput() {
        return input;
    }
}
