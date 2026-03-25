package com.hh.agent.mockemail;

import com.hh.agent.mockemail.model.MockEmail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MockEmailRepository {

    private static final List<MockEmail> EMAILS = buildEmails();

    private MockEmailRepository() {
    }

    public static List<MockEmail> getEmailsPage(int offset, int limit) {
        if (offset < 0 || limit <= 0 || offset >= EMAILS.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(offset + limit, EMAILS.size());
        return new ArrayList<>(EMAILS.subList(offset, end));
    }

    public static boolean hasMore(int loadedCount) {
        return loadedCount < EMAILS.size();
    }

    public static MockEmail findById(String emailId) {
        if (emailId == null) {
            return null;
        }
        for (MockEmail email : EMAILS) {
            if (emailId.equals(email.getId())) {
                return email;
            }
        }
        return null;
    }

    private static List<MockEmail> buildEmails() {
        List<MockEmail> emails = new ArrayList<>();
        emails.add(create(
                "release-brief",
                "产品发布组",
                "Release Brief | 本周版本冻结时间调整",
                "请把提测时间提前到周四 18:00，避免压缩联调窗口。",
                "09:18",
                "2026-03-25 09:18",
                true,
                "INBOX"));
        emails.add(create(
                "security-alert",
                "Security Center",
                "检测到新的 Android 测试设备登录",
                "一台新设备接入了测试账号，请确认是否为本周新增样机。",
                "08:42",
                "2026-03-25 08:42",
                true,
                "IMPORTANT"));
        emails.add(create(
                "design-sync",
                "Design Weekly",
                "首页信息层级评审纪要",
                "搜索框和列表留白先统一，动效延后到下一轮。",
                "昨天",
                "2026-03-24 18:10",
                false,
                "TEAM"));
        emails.add(create(
                "finance-receipt",
                "财务共享",
                "三月报销缺少发票附件",
                "你提交的报销单还缺一张打车发票，请在今天内补齐。",
                "昨天",
                "2026-03-24 14:26",
                false,
                "ACTION"));
        emails.add(create(
                "test-report",
                "QA Bot",
                "Nightly Report: mock-email smoke failed",
                "失败点位集中在列表滚动和详情首屏渲染，日志已归档。",
                "星期一",
                "2026-03-23 07:35",
                true,
                "REPORT"));
        emails.add(create(
                "meeting-room",
                "行政服务",
                "会议室 3A 预订成功",
                "你预订的 15:00-16:00 会议室已确认，可提前十分钟入场。",
                "星期一",
                "2026-03-23 11:00",
                false,
                "INFO"));
        emails.add(create(
                "mentor-note",
                "王老师",
                "关于识别链路拆层的建议",
                "先把观测、决策、动作的边界写清楚，再谈自动兜底。",
                "03/22",
                "2026-03-22 21:06",
                false,
                "PERSONAL"));
        emails.add(create(
                "vendor-contract",
                "采购平台",
                "合同签署提醒",
                "供应商合同将在 48 小时后过期，如需继续合作请尽快完成签章。",
                "03/22",
                "2026-03-22 10:45",
                true,
                "ACTION"));
        emails.add(create(
                "travel-itinerary",
                "Trip Assistant",
                "上海出差行程单已生成",
                "往返高铁和酒店信息已整理到附件，请核对发票抬头。",
                "03/21",
                "2026-03-21 19:30",
                false,
                "TRAVEL"));
        emails.add(create(
                "customer-feedback",
                "客户成功",
                "用户反馈：详情页等待态需要更明确",
                "有用户表示点击邮件后容易误以为卡住，建议补一个正文加载态。",
                "03/21",
                "2026-03-21 16:04",
                false,
                "FEEDBACK"));
        emails.add(create(
                "oss-license",
                "Legal Notice",
                "开源许可证补充说明",
                "请在下个版本把第三方依赖的许可证页面一并补上。",
                "03/20",
                "2026-03-20 12:16",
                false,
                "LEGAL"));
        emails.add(create(
                "family-weekend",
                "妈妈",
                "周末回来吃饭吗",
                "你要是回来，排骨和汤我都一起做。",
                "03/19",
                "2026-03-19 20:08",
                false,
                "PERSONAL"));
        emails.add(create(
                "infra-window",
                "基础设施",
                "测试环境维护窗口通知",
                "今晚 23:00 到 23:40 会重启一批服务，可能影响 smoke。",
                "03/19",
                "2026-03-19 17:12",
                true,
                "OPS"));
        emails.add(create(
                "book-club",
                "读书会",
                "本周阅读章节提醒",
                "这周先读《设计中的设计》第三章，周五晚上统一讨论。",
                "03/18",
                "2026-03-18 09:05",
                false,
                "LIFE"));
        emails.add(create(
                "ci-green",
                "CI Bot",
                "Pipeline recovered on main",
                "主分支流水线已经恢复为绿色，失败用例已转为 flaky 跟踪。",
                "03/17",
                "2026-03-17 23:22",
                false,
                "REPORT"));
        return emails;
    }

    private static MockEmail create(String id,
                                    String sender,
                                    String subject,
                                    String preview,
                                    String receivedTime,
                                    String receivedDate,
                                    boolean unread,
                                    String label) {
        String body = "Hi,\n\n"
                + subject + "\n\n"
                + preview + "\n\n"
                + "这是一封用于 mock 页面演示的邮件正文，内容会比列表摘要更完整。"
                + " 页面打开时会刻意延迟 1-2 秒，用于模拟真实邮件加载和渲染过程。\n\n"
                + "Regards,\n"
                + sender;
        return new MockEmail(id, sender, subject, preview, receivedTime, receivedDate, body, unread, label);
    }
}
