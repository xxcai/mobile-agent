# Contact Selection Reference

本文件补充 `contact_resolver` 中关于“候选联系人选择”的细节规则。

## 核心概念

- 候选序号不是 `contact_id`
- `1`、`2`、`第一个`、`技术部那个` 这类输入，优先解释为“上一轮候选列表中的选择”
- 只有在已经有明确联系人记录时，才可以得到稳定的 `contact_id`

## 续轮选择输入如何解释

以下输入通常表示对上一轮候选结果的选择：

- `1`
- `2`
- `第一个`
- `第二个`
- `技术部那个`
- `市场部那个`
- `就第一个`

此时应：

- 优先调用 `resolve_candidate_selection`
- 不要把这些值直接当成 `contact_id`
- 不要立即调用 `send_im_message`

推荐调用：

```json
{
  "shortcut": "resolve_candidate_selection",
  "args": {
    "selectionText": "第一个",
    "domain": "contact"
  }
}
```

如果返回成功：

- 直接使用返回的 `payload.contact_id`
- 将该结果视为已明确联系人
- 不要重新 `search_contacts`

如果返回失败：

- 若结构化错误里明确给出 `expectedArgs` 或 `argsExample`，先按该格式修正参数再重试一次
- 若仍失败，再决定是否重新 `search_contacts`
- 不要在失败后自行猜测 `contact_id`

## 什么时候可以跳过重新搜索

只有在以下条件同时满足时，才可以跳过重新 `search_contacts`：

- 当前会话里刚刚返回过明确的候选联系人列表
- 用户这一轮只是对候选做选择
- 用户没有改变联系人目标

并且：

- `resolve_candidate_selection` 能成功把当前输入映射到某个候选

## 什么时候必须重新搜索

以下情况应重新 `search_contacts`：

- 当前会话里没有候选联系人结果
- `resolve_candidate_selection` 返回当前输入无法映射到任何候选
- 上一轮候选结果已经不足以判断用户选择的是谁
- 用户改变了联系人目标
- 上一轮候选列表与当前表达明显冲突

## 禁止事项

- 不要把候选序号直接映射成 `contact_id`
- 不要在已经有候选列表的情况下无意义重复搜索
- 不要在联系人尚未明确前直接进入发送动作
