package com.hh.agent.mockbusiness.model;

public class BannerItem {

    private final String title;
    private final String subtitle;
    private final int backgroundResId;
    private final String htmlContent;

    public BannerItem(String title, String subtitle, int backgroundResId, String htmlContent) {
        this.title = title;
        this.subtitle = subtitle;
        this.backgroundResId = backgroundResId;
        this.htmlContent = htmlContent;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public int getBackgroundResId() {
        return backgroundResId;
    }

    public String getHtmlContent() {
        return htmlContent;
    }
}
