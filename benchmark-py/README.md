# benchmark-py

`benchmark-py` 是本仓库里的 PC 端 benchmark 工具。

它负责：

- 通过 `adb` 启动宿主应用并下发 benchmark task
- 轮询设备侧 `.icraw/tasks/<runId>/`
- 把原始日志拉回本地
- 生成派生分析文件

当前这版已经支持两类用法：

- 自动 benchmark：`run-case` / `run-suite`
- 手动测试后分析：`sync-task`

## 环境要求

- Python `>= 3.11`
- `adb`
- 已连接并授权的 Android 设备
- 宿主应用已安装到设备上

默认包名是：

```text
com.hh.agent
```

## 推荐运行方式

推荐使用 `uv`：

```bash
uv run --project benchmark-py benchmark-runner --help
```

如果机器上没有安装 `uv`，也可以自己创建虚拟环境并用 `pip` 安装：

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -e benchmark-py
benchmark-runner --help
```

## 常用命令

### 1. 跑单条 case

```bash
uv run --project benchmark-py benchmark-runner run-case \
  --case benchmark-cases/smoke/settings_change_font_size.json \
  --package com.hh.agent
```

行为：

- 启动宿主 `MainActivity`
- 触发 `run_task`
- 等待任务结束
- 拉取设备侧 task 目录
- 生成本地派生分析文件

### 2. 跑一个 case 目录

```bash
uv run --project benchmark-py benchmark-runner run-suite \
  --cases-dir benchmark-cases/smoke \
  --package com.hh.agent
```

行为：

- 读取目录下全部 `*.json` case
- 按文件名排序串行执行
- 为每条 case 生成独立结果目录
- 额外生成一份 suite 汇总

### 3. 分析设备上的最新 task

适合手动测试后直接分析。

```bash
uv run --project benchmark-py benchmark-runner sync-task \
  --latest \
  --package com.hh.agent
```

行为：

- 找设备上最新的 `.icraw/tasks/<runId>/`
- 拉取到本地
- 生成派生分析文件

### 4. 分析设备上的指定 task

```bash
uv run --project benchmark-py benchmark-runner sync-task \
  --run-id 20260414-113740-f4d2c7a1 \
  --package com.hh.agent
```

### 5. 只分析本地 raw 日志

```bash
uv run --project benchmark-py benchmark-runner derive \
  --raw-dir build/benchmark-results/20260414-113740-f4d2c7a1/raw \
  --derived-dir build/benchmark-results/20260414-113740-f4d2c7a1/derived
```

### 6. 查看设备上有哪些 task

```bash
uv run --project benchmark-py benchmark-runner list-device-tasks \
  --package com.hh.agent
```

## case 格式

当前 case 先用最小 JSON 结构：

```json
{
  "id": "settings_change_font_size",
  "prompt": "把设置里的字体大小调大"
}
```

## 输出目录

默认输出到：

```text
build/benchmark-results/
```

单条 run 的目录结构：

```text
build/benchmark-results/<runId>/
  case.json
  raw/
    meta.json
    events.jsonl
    response.txt
  derived/
    summary.json
    timeline.txt
    tool-events/
```

解析后的展示目录：

```text
build/benchmark-results-view/<runId>-<displayName>/
  summary.json
  timeline.txt
  tool-events/
```

手动分析默认输出到：

```text
build/benchmark-results/manual/<runId>/
```

## 关键产物说明

- `raw/meta.json`
  端上任务元信息
- `raw/events.jsonl`
  agent 原始事件流
- `raw/response.txt`
  最终文本累计结果
- `derived/summary.json`
  轻量摘要，当前包含：
  - `runId`
  - `taskId`
  - `displayName`
  - `status`
  - `errorMessage`
  - `finishReason`
  - `durationSec`
  - `toolCallCount`
- `build/benchmark-results-view/<runId>-<displayName>/`
  面向人工查看的派生结果目录，`displayName` 来自 `raw/meta.json.displayName`
- `derived/timeline.txt`
  面向人工排查的时间线，包含：
  - `reasoning`
  - `tool_use`
  - `tool_result`
  - `text`
  - `message_end`

## 当前边界

当前这套工具已经能稳定做：

- benchmark 执行
- 设备日志拉取
- 行为分析
- `max_iterations` 等停止原因识别

当前还不做：

- 任务语义成功/失败自动判分
- 复杂 grader
- 多设备调度
- 环境 reset

也就是说，当前更准确的定位是：

- execution benchmark
- transcript / behavior analysis tool

而不是最终版 task success benchmark。
