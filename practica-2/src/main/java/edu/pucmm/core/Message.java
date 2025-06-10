package edu.pucmm.core;

public class Message {
    private int sourceId;
    private int destinationId;
    private String content;
    private long timestamp;

    public Message(int sourceId, int destinationId, String content, long timestamp) {
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.content = content;
        this.timestamp = timestamp;
    }

    public int getSourceId() {
        return sourceId;
    }

    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }

    public int getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(int destinationId) {
        this.destinationId = destinationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}