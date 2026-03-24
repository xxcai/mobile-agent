package com.hh.agent.mockim;

import com.hh.agent.mockim.model.ChatConversation;
import com.hh.agent.mockim.model.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MockChatRepository {

    private static final List<ChatConversation> CONVERSATIONS = buildConversations();

    private MockChatRepository() {
    }

    public static List<ChatConversation> getConversations() {
        return Collections.unmodifiableList(CONVERSATIONS);
    }

    public static ChatConversation findConversationById(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        for (ChatConversation conversation : CONVERSATIONS) {
            if (conversationId.equals(conversation.getId())) {
                return conversation;
            }
        }
        return null;
    }

    private static List<ChatConversation> buildConversations() {
        List<ChatConversation> conversations = new ArrayList<>();

        conversations.add(new ChatConversation(
                "team-sync",
                "项目同步群",
                "3",
                "09:18",
                "下午把识别测试场景补齐，我再走一轮自动化。",
                true,
                createMessages(
                        pair(false, "今天先把 mock 聊天场景搭出来。"),
                        pair(true, "我已经在首页塞了几组不同状态的数据。"),
                        pair(false, "记得加未读数和置顶，这样识别更接近真实场景。"),
                        pair(true, "好，我把群聊和单聊都混进去。"),
                        pair(false, "下午把识别测试场景补齐，我再走一轮自动化。"),
                        pair(true, "收到，详情页我也放一些左右气泡。")
                )));

        conversations.add(new ChatConversation(
                "zhangsan",
                "张三",
                "",
                "08:42",
                "早上十点楼下咖啡店见。",
                false,
                createMessages(
                        pair(true, "你到公司了吗？"),
                        pair(false, "刚到，正在电梯里。"),
                        pair(true, "那我们十点碰一下。"),
                        pair(false, "行，楼下咖啡店见。"),
                        pair(true, "我会带着那份 UI 标注文档。"),
                        pair(false, "好。")
                )));

        conversations.add(new ChatConversation(
                "ops-alert",
                "监控告警",
                "12",
                "昨天",
                "[告警] mock-im-app 页面渲染检查失败",
                true,
                createMessages(
                        pair(false, "【09:30】首页渲染检查失败。"),
                        pair(false, "【09:31】ChatListFragment 加载超时。"),
                        pair(true, "这是测试环境吗？"),
                        pair(false, "是，mock 数据接口未就绪。"),
                        pair(true, "先切回本地数据源。"),
                        pair(false, "已恢复，等待复测。")
                )));

        conversations.add(new ChatConversation(
                "mom",
                "妈妈",
                "",
                "昨天",
                "记得周末回来吃饭。",
                false,
                createMessages(
                        pair(false, "这周六中午回来吗？"),
                        pair(true, "回，下午还要带电脑。"),
                        pair(false, "那我做你爱吃的排骨。"),
                        pair(true, "好。"),
                        pair(false, "别又忙到太晚。"),
                        pair(true, "知道啦。")
                )));

        conversations.add(new ChatConversation(
                "design-review",
                "设计评审",
                "1",
                "星期日",
                "首页结构先别抠细节，先保证识别稳定。",
                false,
                createMessages(
                        pair(true, "mock app 这轮先不做复杂动效。"),
                        pair(false, "同意，先把一级页和详情页打通。"),
                        pair(true, "重点是让 Agent 能稳定识别列表和会话。"),
                        pair(false, "那头像、时间、未读都先做出差异化。"),
                        pair(true, "首页结构先别抠细节，先保证识别稳定。"),
                        pair(false, "可以。")
                )));

        conversations.add(new ChatConversation(
                "file-helper",
                "文件传输助手",
                "",
                "星期六",
                "截图路径：/sdcard/mock-ui/chat-list.png",
                false,
                createMessages(
                        pair(true, "截图路径：/sdcard/mock-ui/chat-list.png"),
                        pair(true, "导出一份聊天详情页也放这里。"),
                        pair(false, "已收到两张截图。"),
                        pair(true, "回头用来做 OCR 和控件识别对比。"),
                        pair(false, "好的。"),
                        pair(true, "记得保留顶部和底部导航。")
                )));

        addConversation(conversations, "qa-daily", "QA 日报群", "5", "星期六", "今天回归重点是聊天列表滚动和详情页返回。",
                true,
                "今天主要看列表滚动是否顺畅。",
                "我这边会补 30 条数据。",
                "别忘了看低分辨率机型。",
                "好，我顺便测一下点击命中。");
        addConversation(conversations, "product-li", "产品小李", "", "星期五", "这版先不用加真实发送能力。",
                false,
                "首页先像微信就够了。",
                "收到，这轮只做静态场景。",
                "你把列表做长一点。",
                "明白。");
        addConversation(conversations, "android-dev", "Android 讨论组", "18", "星期五", "adaptive icon 那边已经切成 mipmap 方案。",
                true,
                "图标需要 roundIcon 一起配。",
                "已经补了。",
                "那再看下低版本 fallback。",
                "也加上了。");
        addConversation(conversations, "xuemei", "学妹", "", "星期四", "谢谢你昨天发我的资料。",
                false,
                "昨天那份面试题我看了。",
                "还行吗？",
                "挺有帮助的。",
                "那就好。");
        addConversation(conversations, "release-bot", "发布助手", "2", "星期四", "debug 包已生成：app-debug.apk",
                false,
                "本次构建已完成。",
                "产物路径我发群里了。",
                "收到。",
                "你们安装后看一下图标。");
        addConversation(conversations, "study-group", "算法夜聊群", "9", "星期三", "今晚八点继续讲图搜索。",
                false,
                "今天讲 BFS 和双向 BFS。",
                "我可能晚十分钟。",
                "没事，到时看回放。",
                "好。");
        addConversation(conversations, "designer-zhou", "设计周周", "", "星期三", "搜索框颜色可以再淡一点。",
                false,
                "顶部留白够吗？",
                "现在先不抠细节。",
                "那我先只看结构。",
                "对。");
        addConversation(conversations, "ops-night", "值班群", "7", "星期三", "凌晨三点有一台测试机离线了。",
                true,
                "离线那台是自动化专用机。",
                "已经重启。",
                "现在恢复了吗？",
                "恢复了。");
        addConversation(conversations, "travel", "周末出游", "", "星期二", "酒店我先订双床房了。",
                false,
                "车票你买了吗？",
                "我今晚下单。",
                "那我先把行程发群里。",
                "行。");
        addConversation(conversations, "mentor-wang", "王老师", "", "星期二", "界面识别要先明确观测和动作分层。",
                false,
                "你现在先把测试场景做全。",
                "后面再谈执行链路。",
                "好，我先扩一下列表数据。",
                "继续。");
        addConversation(conversations, "finance", "财务通知", "1", "星期二", "请尽快补充报销单据扫描件。",
                false,
                "本月报销还差发票。",
                "我明天补。",
                "记得附行程单。",
                "收到。");
        addConversation(conversations, "book-club", "读书会", "", "星期一", "这周读《设计中的设计》第三章。",
                false,
                "你们做完笔记发群里。",
                "我可能周末补。",
                "没关系。",
                "到时一起讨论。");
        addConversation(conversations, "ci-bot", "CI 机器人", "4", "星期一", "mock-im-app 分支新增提交 8ebb154",
                true,
                "新的提交已经推送。",
                "包含 mock IM 和 launcher icon。",
                "后面还会继续补数据。",
                "好的，等下一次流水线。");
        addConversation(conversations, "college-roommates", "大学宿舍", "", "03/17", "谁还记得毕业照原图放哪了？",
                false,
                "我电脑里好像有。",
                "你回头翻一下。",
                "行，晚上找。",
                "谢了。");
        addConversation(conversations, "reading-note", "读书摘抄", "", "03/17", "今天记一句：好的系统先定义边界。",
                false,
                "这句挺适合现在这个项目。",
                "确实。",
                "先把边界说清楚，后面好做。",
                "嗯。");
        addConversation(conversations, "hackathon", "Hackathon 总群", "23", "03/16", "周六上午十点统一抽签分组。",
                false,
                "题目范围会提前公布吗？",
                "不会，现场抽。",
                "那我得先准备下 Demo。",
                "对。");
        addConversation(conversations, "sister", "姐姐", "", "03/16", "家里的路由器我帮你重新配好了。",
                false,
                "现在网稳定多了。",
                "那就行。",
                "密码没变。",
                "收到。");
        addConversation(conversations, "mock-scenes", "识别场景素材群", "6", "03/15", "还差弹窗场景和输入框聚焦态。",
                true,
                "列表长度这轮先补够。",
                "然后再做弹窗。",
                "详情页也可以多加几类消息。",
                "排到下一步。");
        addConversation(conversations, "gym", "健身搭子", "", "03/15", "今晚练腿还是练背？",
                false,
                "我今天可能加班。",
                "那就周末约。",
                "可以。",
                "行。");
        addConversation(conversations, "homeowners", "业主群", "15", "03/14", "地下车库照明今晚十点检修。",
                false,
                "会影响到充电桩吗？",
                "通知里没写。",
                "那我先把车开出来。",
                "稳妥。");
        addConversation(conversations, "teacher-liu", "刘教练", "", "03/14", "明天早训改到七点半。",
                false,
                "收到。",
                "别迟到。",
                "不会。",
                "好。");
        addConversation(conversations, "security", "安全中心", "8", "03/13", "检测到一台新设备登录测试账号。",
                true,
                "是你们新接的那台手机吗？",
                "是的。",
                "那我先放行。",
                "谢谢。");
        addConversation(conversations, "coffee", "咖啡拼单", "", "03/13", "有人要冰美式吗？差一杯免配送。",
                false,
                "我要一杯少冰。",
                "我来一杯燕麦拿铁。",
                "好，我统一下单。",
                "谢啦。");
        addConversation(conversations, "pm-sync", "需求同步", "3", "03/12", "你先把新增需求记录到规划目录。",
                false,
                "规划文件要单独放目录里。",
                "收到，已经调整了。",
                "后面继续按这个结构走。",
                "好。");
        addConversation(conversations, "family", "家庭群", "11", "03/12", "周日中午一起给外婆过生日。",
                false,
                "蛋糕我来订。",
                "我带水果。",
                "那我负责接人。",
                "行。");

        return conversations;
    }

    private static void addConversation(List<ChatConversation> conversations,
                                        String id,
                                        String title,
                                        String unreadCount,
                                        String time,
                                        String lastMessage,
                                        boolean pinned,
                                        String... messages) {
        conversations.add(new ChatConversation(
                id,
                title,
                unreadCount,
                time,
                lastMessage,
                pinned,
                createAlternatingMessages(messages)
        ));
    }

    private static List<ChatMessage> createAlternatingMessages(String... contents) {
        List<ChatMessage> list = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            list.add(new ChatMessage(i % 2 == 1, contents[i]));
        }
        return list;
    }

    private static List<ChatMessage> createMessages(ChatMessage... messages) {
        List<ChatMessage> list = new ArrayList<>();
        Collections.addAll(list, messages);
        return list;
    }

    private static ChatMessage pair(boolean fromMe, String content) {
        return new ChatMessage(fromMe, content);
    }
}
