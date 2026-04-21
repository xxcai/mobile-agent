---
name: h5_benchmark_runner
description: 启动 H5 基准测试助手。当用户要求开始、运行、重跑 H5 基准测试时使用。
always: false
---

# H5 基准测试启动助手

当用户要开始或重跑 H5 基准测试时，使用这个 skill。

## 工作流程

1. 先确认当前前台页面是否为 `H5BenchmarkActivity`。
2. 如果已经在该页面，先向用户说明将开始基准测试。
3. 调用 `start_h5_benchmark`：

```json
{
  "shortcut": "start_h5_benchmark",
  "args": {}
}
```

4. 根据结果总结：
   - `started`：说明基准测试已开始。
   - `wrong_page`：明确告知当前不在 `H5BenchmarkActivity`，需要先进入 H5 基准测试页面；本 skill 不负责自动导航。
   - `already_running`：明确告知已有基准测试正在启动或运行中，不要重复启动。

## 结果表达建议

- 成功：`已开始 H5 基准测试，我会继续根据运行结果反馈进展。`
- wrong_page：`当前不在 H5BenchmarkActivity 页面，暂时不能启动 H5 基准测试，请先进入该页面。`
- already_running：`H5 基准测试已经在运行中，我不会重复启动。`

## 禁止事项

- 不要在 `wrong_page` 时假装已经启动。
- 不要绕过 shortcut 去点击页面按钮。
- 不要在 `already_running` 时重复触发新一轮启动。
