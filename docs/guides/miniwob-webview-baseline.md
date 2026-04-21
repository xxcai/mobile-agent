# MiniWoB WebView Baseline Guide

## 入口

- app 首页按钮：`打开H5基准测试`
- 页面：`H5BenchmarkActivity`

## 固定配置

- suite id：`miniwob-v0-baseline-20`
- 资源父目录：`app/src/main/assets/web/h5bench/`
- 任务页加载路径：`file:///android_asset/web/h5bench/miniwob/<task>.html`

当前实现中的固定任务清单位于：

- `app/src/main/assets/web/h5bench/miniwob_v0_baseline_20.json`

## 运行路径

1. 从 app 首页进入 `H5BenchmarkActivity`
2. 点击“开始基准测试预览”
3. 页面会加载固定 suite 的首个任务，并通过 `BusinessWebActivity` 以 benchmark 模式打开
4. WebView 直接加载完整 asset URL，而不是拼接 HTML 字符串

## 评分口径

- 主分：`successRate`
- 辅助指标：
  - `avgSuccessSteps`
  - `avgSuccessLatencyMs`
  - `timeoutRate`
  - `avgReward`

其中：

- `avgSuccessSteps` 和 `avgSuccessLatencyMs` 只统计成功任务
- `timeoutRate` 统计 `finishReason == timeout` 的任务占比

## Run 输出字段

每轮 run 至少记录以下字段：

- `runId`
- `model`
- `provider`
- `promptVersion`
- `suiteId`
- `successRate`
- `avgSuccessSteps`
- `avgSuccessLatencyMs`
- `timeoutRate`

示例：

```json
{
  "runId": "2026-04-18-gpt-4.1-run01",
  "model": "gpt-4.1",
  "provider": "openai",
  "promptVersion": "workspace@v1",
  "suiteId": "miniwob-v0-baseline-20",
  "successRate": 42.0,
  "avgSuccessSteps": 6.8,
  "avgSuccessLatencyMs": 4900,
  "timeoutRate": 18.0
}
```

## 对比维度

第一版对比维度固定为：

- `model`

结果页至少展示：

- 单轮 summary
- 按模型维度的对比表
- 任务级 diff 区域
