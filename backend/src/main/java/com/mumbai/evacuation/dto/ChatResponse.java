package com.mumbai.evacuation.dto;

import java.util.List;

public class ChatResponse {
    private String reply;
    private long timestamp;
    private boolean activeDisastersPresent;
    private List<String> suggestedActions;

    public ChatResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ChatResponse(String reply, boolean activeDisastersPresent, List<String> suggestedActions) {
        this.reply = reply;
        this.timestamp = System.currentTimeMillis();
        this.activeDisastersPresent = activeDisastersPresent;
        this.suggestedActions = suggestedActions;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isActiveDisastersPresent() {
        return activeDisastersPresent;
    }

    public void setActiveDisastersPresent(boolean activeDisastersPresent) {
        this.activeDisastersPresent = activeDisastersPresent;
    }

    public List<String> getSuggestedActions() {
        return suggestedActions;
    }

    public void setSuggestedActions(List<String> suggestedActions) {
        this.suggestedActions = suggestedActions;
    }
}
