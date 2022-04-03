package com.sachett.samosa.playground.models;

import org.springframework.data.mongodb.core.mapping.Document;

@Document("programs")
public class ProgramDocument {

    private String program;
    private String output;
    private String error;
    private String[] inputs;
    private String sessionId;

    public ProgramDocument(String program,
                           String output, String error,
                           String[] inputs,
                           String sessionId) {
        super();
        this.program = program;
        this.output = output;
        this.error = error;
        this.inputs = inputs;
        this.sessionId = sessionId;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String[] getInputs() {
        return inputs;
    }

    public void setInputs(String[] inputs) {
        this.inputs = inputs;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
