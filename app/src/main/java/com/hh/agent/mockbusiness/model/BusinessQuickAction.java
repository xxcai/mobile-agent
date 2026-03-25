package com.hh.agent.mockbusiness.model;

public class BusinessQuickAction {

    private final String title;
    private final String subtitle;
    private final int iconResId;
    private final int iconBackgroundResId;
    private final String htmlContent;

    public BusinessQuickAction(String title,
                               String subtitle,
                               int iconResId,
                               int iconBackgroundResId,
                               String htmlContent) {
        this.title = title;
        this.subtitle = subtitle;
        this.iconResId = iconResId;
        this.iconBackgroundResId = iconBackgroundResId;
        this.htmlContent = htmlContent;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getIconBackgroundResId() {
        return iconBackgroundResId;
    }

    public String getHtmlContent() {
        return htmlContent;
    }
}
