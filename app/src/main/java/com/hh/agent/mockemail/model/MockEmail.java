package com.hh.agent.mockemail.model;

public class MockEmail {

    private final String id;
    private final String sender;
    private final String subject;
    private final String preview;
    private final String receivedTime;
    private final String receivedDate;
    private final String body;
    private final boolean unread;
    private final String label;

    public MockEmail(String id,
                     String sender,
                     String subject,
                     String preview,
                     String receivedTime,
                     String receivedDate,
                     String body,
                     boolean unread,
                     String label) {
        this.id = id;
        this.sender = sender;
        this.subject = subject;
        this.preview = preview;
        this.receivedTime = receivedTime;
        this.receivedDate = receivedDate;
        this.body = body;
        this.unread = unread;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public String getPreview() {
        return preview;
    }

    public String getReceivedTime() {
        return receivedTime;
    }

    public String getReceivedDate() {
        return receivedDate;
    }

    public String getBody() {
        return body;
    }

    public boolean isUnread() {
        return unread;
    }

    public String getLabel() {
        return label;
    }
}
