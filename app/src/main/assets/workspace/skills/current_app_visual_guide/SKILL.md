---
name: current_app_visual_guide
description: 当前应用原生界面的页面导览图。仅当 Agent 需要在当前应用内进行页面定位、路线规划、入口查找，或结合当前观察结果理解页面结构时使用。用于帮助 Agent 理解一级导航、常见页面分区、弹层和功能入口分布，不用于纯文本问答、通用业务知识回答或脱离当前界面的任务。
always: false
---

# Current App Visual Guide

这份 skill 提供当前应用原生界面的导览信息，重点描述一级导航、常见页面结构、功能分区、弹层形态和入口分布。

只有当任务目标与当前应用内的页面定位、入口查找、路线规划或界面结构理解有关时，才应使用本 skill。

## 触发条件

- Agent 需要在当前应用内定位某个页面、模块或功能入口
- Agent 需要规划从当前位置到目标区域的大致路线
- Agent 需要结合当前观察结果理解页面结构、功能分区或弹层内容
- Agent 需要判断某类目标通常位于哪个页面或哪个区域

## 适用范围

- 只适用于当前应用的原生界面
- 适用于可以稳定观察到标题、Tab、列表、按钮和内容区域的页面
- 可用于规划前往某个目标页面的路线，即使目标本身最终可能是 H5 页面
- 本 skill 只描述当前应用原生界面的布局与分区，不展开描述 H5 页面内部结构
- 不用于纯文本任务、纯 route 解析或不依赖当前界面的业务问题
- 不用于通用产品介绍或脱离页面上下文的业务知识问答

## 使用重点

- 关注当前应用的整体布局，而不是单次实现细节
- 理解一级导航、页面骨架和功能分区
- 理解“某类目标通常会出现在哪个区域”
- 把每个页面看作一个功能区域，而不是一组技术元素
- 重点说明“这个区域通常放什么”和“这个页面大概是做什么的”

## App 一级结构

当前应用底部存在五个一级 Tab：

- 消息
- 邮件
- 通讯录
- 业务
- 知识

整体结构说明见 `references/app_map.md`。

## 已整理页面

- 一级结构总览见 `references/app_map.md`
- 消息页布局见 `references/message_page.md`
- 邮件页布局见 `references/mail_page.md`
- 通讯录页布局见 `references/contacts_page.md`
- 业务页布局见 `references/business_page.md`
- 知识页布局见 `references/knowledge_page.md`
- 个人中心布局见 `references/profile_page.md`
- 搜索页布局见 `references/search_page.md`
- 主界面快捷操作弹层见 `references/home_quick_actions_popup.md`
