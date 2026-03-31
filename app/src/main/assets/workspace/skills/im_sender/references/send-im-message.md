# Send IM Message Reference

本文件补充 `im_sender` 中关于 `send_im_message` 的调用约束和错误处理细节。

## 调用前提

只有在以下条件同时满足时，才应调用 `send_im_message`：

- 已经有明确的 `contact_id`
- 消息内容已明确

如果联系人尚未明确，应先回到 `contact_resolver`。

## 调用格式

```json
{
  "shortcut": "send_im_message",
  "args": {
    "contact_id": "001",
    "message": "今天加班"
  }
}
```

## 成功条件

只有当前回合 `send_im_message` 返回成功，才能向用户确认：

- “已发送”
- “已再次发送”

不能用自然语言推断代替执行证据。

## 常见错误

### `missing_required_param`

含义：

- 调用参数不完整

处理：

- 补齐必要参数
- 不要直接重试同样请求

### `business_target_not_accessible`

含义：

- 当前联系人目标不能直接发送

处理：

- 明确告诉用户当前目标不可直接触达
- 不要伪装为发送成功

### `execution_failed`

含义：

- 执行过程失败

处理：

- 给出简洁失败说明
- 建议稍后重试
