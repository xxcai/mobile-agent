package com.hh.agent.mockdiscover;

import java.util.ArrayList;
import java.util.List;

public final class MockDiscoverRepository {

    private MockDiscoverRepository() {
    }

    public static List<DiscoverMoment> getMoments() {
        List<DiscoverMoment> moments = new ArrayList<>();
        moments.add(new DiscoverMoment(
                "小雨",
                "10分钟前",
                "今天终于把迪士尼攻略走完了，早上七点半入园，雷鸣山和飞跃地平线都刷到了。晚上烟花真的值回票价。",
                "上海迪士尼",
                "配图：城堡夜景、入园自拍、烟花视频截图",
                48,
                12));
        moments.add(new DiscoverMoment(
                "老周",
                "28分钟前",
                "部门今天第一次把新的周报模板跑起来，结论很清楚：本周最花时间的是回归测试和线上日志排查。",
                "",
                "",
                21,
                6));
        moments.add(new DiscoverMoment(
                "阿杰",
                "1小时前",
                "周末骑了 42 公里，江边风很舒服。回来的时候顺手买了三斤草莓，准备晚上做酸奶碗。",
                "滨江绿道",
                "配图：公路车靠在护栏边，草莓装满后座包",
                37,
                9));
        moments.add(new DiscoverMoment(
                "Momo",
                "2小时前",
                "今天的猫比我先下班，趴在键盘上把 review 挡得严严实实。最后只好抱着它一起开会。",
                "",
                "配图：橘猫压在 MacBook 键盘上",
                63,
                18));
        moments.add(new DiscoverMoment(
                "产品小李",
                "今天 13:20",
                "中午和设计、研发一起过了发现页改版，先不追求全量重构，第一版把信息密度和首屏理解做好就行。",
                "公司 12F 小会议室",
                "",
                16,
                3));
        moments.add(new DiscoverMoment(
                "小楠",
                "今天 11:05",
                "周末去安吉住了两天，民宿后山的竹林很安静。最喜欢早上雾刚散的时候，空气像被洗过一样。",
                "安吉",
                "配图：竹林步道、山间早餐、窗外起雾的茶田",
                54,
                14));
        moments.add(new DiscoverMoment(
                "健身搭子",
                "今天 08:14",
                "腿日完成。深蹲 100kg 终于做到了 5x5，下次可以试试把硬拉也拉回训练计划里。",
                "",
                "",
                29,
                7));
        moments.add(new DiscoverMoment(
                "读书会",
                "昨天 22:48",
                "这周读《置身事内》，大家普遍对土地财政那章讨论最多。原来很多宏观现象都能落到地方治理逻辑上。",
                "",
                "配图：书页划线、咖啡杯、线下讨论白板",
                18,
                5));
        return moments;
    }
}
