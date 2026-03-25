package com.hh.agent.mockbusiness;

import com.hh.agent.R;
import com.hh.agent.mockbusiness.model.BannerItem;
import com.hh.agent.mockbusiness.model.BusinessQuickAction;
import com.hh.agent.mockbusiness.model.TodoItem;

import java.util.Arrays;
import java.util.List;

public final class MockBusinessRepository {

    private MockBusinessRepository() {
    }

    public static List<BusinessQuickAction> getQuickActions() {
        return Arrays.asList(
                action("审批中心", "3 条待审批", android.R.drawable.ic_menu_agenda, R.drawable.bg_business_icon_blue),
                action("销售看板", "本周转化 24%", android.R.drawable.ic_menu_sort_by_size, R.drawable.bg_business_icon_green),
                action("客户拜访", "下午 2 场", android.R.drawable.ic_menu_my_calendar, R.drawable.bg_business_icon_orange),
                action("报表中心", "日报已生成", android.R.drawable.ic_menu_edit, R.drawable.bg_business_icon_cyan),
                action("知识库", "更新 12 篇", android.R.drawable.ic_menu_info_details, R.drawable.bg_business_icon_pink),
                action("项目管理", "5 个风险点", android.R.drawable.ic_menu_manage, R.drawable.bg_business_icon_blue),
                action("工单系统", "2 条超时", android.R.drawable.ic_menu_send, R.drawable.bg_business_icon_red),
                action("费用申请", "本月预算 68%", android.R.drawable.ic_menu_save, R.drawable.bg_business_icon_green)
        );
    }

    public static List<BannerItem> getBannerItems() {
        return Arrays.asList(
                banner("春季业务增长计划", "新增客户专项激励上线，点击查看规则。", R.drawable.bg_business_banner_1),
                banner("Q2 重点项目协同", "跨部门资源预约入口已开放。", R.drawable.bg_business_banner_2),
                banner("企业培训月", "管理者训练营本周开始报名。", R.drawable.bg_business_banner_3)
        );
    }

    public static List<TodoItem> getTodoItems() {
        return Arrays.asList(
                todo("提交华东区周报", "截止今天 18:00", true),
                todo("确认周四客户演示名单", "还差 2 位参会人", false),
                todo("补充 3 月差旅发票", "财务已二次提醒", false),
                todo("完成 OKR 自评", "距离截止还有 1 天", true)
        );
    }

    public static String getMeetingHtml() {
        return buildHtmlSection(
                "会议提醒",
                "15:00 - 15:45 产品需求评审",
                "地点：3A 会议室 / 线上同步飞书会议\n参会人：产品、设计、客户端、测试\n会前资料：请先确认最新交互稿和风险清单。");
    }

    public static String getAttendanceHtml() {
        return buildHtmlSection(
                "打卡中心",
                "今日打卡状态",
                "首次打卡：09:06\n最后打卡：18:41\n当前状态：已完成上下班打卡\n建议：明天上午 9 点前完成早签到。");
    }

    public static String getTodoHtml() {
        return buildHtmlSection(
                "待办列表",
                "今日重点任务",
                "1. 提交华东区周报\n2. 确认客户演示名单\n3. 补充差旅发票\n4. 完成 OKR 自评");
    }

    public static String getMoreHtml() {
        return buildHtmlSection(
                "业务总览",
                "入口说明",
                "当前页面为 mock 业务首页，所有图标和卡片点击后统一进入本地 HTML Web 页面，用于后续替换为真实 H5 页面。");
    }

    private static BusinessQuickAction action(String title, String subtitle, int iconResId, int backgroundResId) {
        return new BusinessQuickAction(
                title,
                subtitle,
                iconResId,
                backgroundResId,
                buildHtmlSection(title, subtitle, "这是 " + title + " 的本地 mock 页面内容，可在这里继续扩展成真实业务 H5。"));
    }

    private static BannerItem banner(String title, String subtitle, int backgroundResId) {
        return new BannerItem(
                title,
                subtitle,
                backgroundResId,
                buildHtmlSection(title, subtitle, "Banner 点击后进入统一复用的 Web 页面，当前展示本地 HTML 内容。"));
    }

    private static TodoItem todo(String title, String subtitle, boolean highlighted) {
        return new TodoItem(
                title,
                subtitle,
                highlighted,
                buildHtmlSection(title, subtitle, "待办详情仍然走同一个本地 HTML 页面，后续可以替换成真实待办详情。"));
    }

    private static String buildHtmlSection(String heading, String summary, String detail) {
        return "<h2>" + heading + "</h2><p>" + summary + "</p><p>" + detail.replace("\n", "<br/>") + "</p>";
    }
}
