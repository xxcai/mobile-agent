package com.hh.agent.mockdiscover;

public class DiscoverMoment {

    private final String author;
    private final String time;
    private final String content;
    private final String location;
    private final String mediaHint;
    private final int likeCount;
    private final int commentCount;

    public DiscoverMoment(String author,
                          String time,
                          String content,
                          String location,
                          String mediaHint,
                          int likeCount,
                          int commentCount) {
        this.author = author;
        this.time = time;
        this.content = content;
        this.location = location;
        this.mediaHint = mediaHint;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
    }

    public String getAuthor() {
        return author;
    }

    public String getTime() {
        return time;
    }

    public String getContent() {
        return content;
    }

    public String getLocation() {
        return location;
    }

    public String getMediaHint() {
        return mediaHint;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }
}
