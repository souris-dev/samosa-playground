package com.sachett.samosa.playground.models;

public class RunResponse {
    private String output;
    private String error;
    private boolean containsError;
    private boolean isEndRun;

    public RunResponse() {}

    public RunResponse(String output, String error, boolean containsError) {
        this.output = output;
        this.error = error;
        this.containsError = containsError;
    }

    public RunResponse(String output, String error, boolean containsError, boolean isEndRun) {
        this.output = output;
        this.error = error;
        this.containsError = containsError;
        this.isEndRun = isEndRun;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public boolean getContainsError() {
        return containsError;
    }

    public boolean isEndRun() {
        return isEndRun;
    }

    public String toJson() {
        return "{"
                + "\"output\":\"" + getOutput()
                + "\",\"error\":\"" + getError()
                + "\",\"is_error\":" + getContainsError()
                + "\",\"is_end_run\":" + isEndRun()
                + "}";
    }
}
