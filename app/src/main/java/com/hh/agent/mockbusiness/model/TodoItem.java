package com.hh.agent.mockbusiness.model;

public class TodoItem {

    private final String title;
    private final String subtitle;
    private final boolean highlighted;
    private final String htmlContent;

    public TodoItem(String title, String subtitle, boolean highlighted, String htmlContent) {
        this.title = title;
        this.subtitle = subtitle;
        this.highlighted = highlighted;
        this.htmlContent = htmlContent;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public String getHtmlContent() {
        return htmlContent;
    }
}
