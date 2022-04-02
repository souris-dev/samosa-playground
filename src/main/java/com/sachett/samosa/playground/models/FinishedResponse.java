package com.sachett.samosa.playground.models;

public class FinishedResponse {
    private final boolean finished;
    private final String finishText;

    public FinishedResponse(boolean finished) {
        this.finished = finished;
        this.finishText = "<b style=\"color:#676767\">Program has exited.</b>";
    }

    public FinishedResponse(boolean finished, String finishText) {
        this.finished = finished;
        this.finishText = finishText;
    }

    public boolean getFinished() {
        return finished;
    }

    public String getFinishText() {
        return finishText;
    }
}
