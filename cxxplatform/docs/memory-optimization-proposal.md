# icraw 记忆系统优化建议

> 基于对 OpenClaw 记忆压缩系统的深度调研，为 icraw 提出的记忆优化方案

## 一、现状分析

### 1.1 当前 icraw 记忆系统

| 组件 | 实现状态 | 问题 |
|------|---------|------|
| 存储后端 | SQLite | ✅ 良好 |
| 消息历史 | `messages` 表 | ⚠️ 无压缩，无限增长 |
| 搜索功能 | `LIKE` 模糊匹配 | ⚠️ 性能差，无语义理解 |
| 记忆整合 | 手动触发 | ⚠️ 无自动压缩机制 |
| 总结存储 | `summaries` 表 | ⚠️ 未在上下文中使用 |

### 1.2 当前配置

```cpp
// config.hpp
struct AgentConfig {
    int memory_window = 50;           // 保留消息数
    int consolidation_threshold = 30; // 触发整合阈值
};
```

**问题**：
- 阈值太低，频繁触发整合
- 无 token 计数，无法精确控制上下文
- 整合后旧消息仍保留，存储持续增长

---

## 二、OpenClaw 最佳实践参考

### 2.1 核心机制对比

| 机制 | OpenClaw | icraw 当前 | 建议 |
|------|----------|-----------|------|
| 预压缩保存 | Memory Flush | ❌ 无 | **高优先级** |
| 上下文压缩 | Compaction | 简单整合 | 需增强 |
| 工具结果裁剪 | Session Pruning | ❌ 无 | 中优先级 |
| 混合搜索 | BM25 + Vector | LIKE | 中优先级 |
| 标识符保留 | 强制保留 | ❌ 无 | **高优先级** |

### 2.2 关键算法参数

```
OpenClaw 默认值：
├── SUMMARIZATION_OVERHEAD_TOKENS = 4096
├── SAFETY_MARGIN = 1.2 (20% 缓冲)
├── BASE_CHUNK_RATIO = 0.4
├── MIN_CHUNK_RATIO = 0.15
├── DEFAULT_MEMORY_FLUSH_SOFT_TOKENS = 4000
└── maxHistoryShare = 0.5 (历史占上下文 50%)
```

---

## 三、优化方案

### 3.1 方案一：预压缩保存（Memory Flush）

**目标**：在上下文压缩前，主动保存重要信息到长期记忆

#### 3.1.1 触发条件

```cpp
// 新增配置
struct CompactionConfig {
    bool memory_flush_enabled = true;
    int reserve_tokens_floor = 20000;      // 为新消息保留的最小 tokens
    int memory_flush_soft_threshold = 4000; // 提前触发 flush 的缓冲
    int context_window_tokens = 128000;     // 模型上下文窗口大小
};

// 触发逻辑
bool should_run_memory_flush(int total_tokens, int context_window, 
                             int reserve_floor, int soft_threshold) {
    int threshold = context_window - reserve_floor - soft_threshold;
    return total_tokens >= threshold;
}
```

#### 3.1.2 提示词设计

**系统提示词**：
```
Pre-compaction memory flush turn.
The session is near auto-compaction; capture durable memories to disk.
You may reply, but usually NO_REPLY is correct.
```

**用户提示词**：
```
Pre-compaction memory flush.
Store durable memories now (use save_memory tool).
IMPORTANT: Focus on:
- User preferences and decisions
- Key facts learned about the user
- Important context for future conversations
- Pending tasks or reminders

If nothing important to store, reply with NO_REPLY.
Current time: {current_datetime}
```

#### 3.1.3 实现建议

```cpp
// memory_manager.hpp 新增
class MemoryManager {
public:
    // 检查是否需要 memory flush
    bool should_flush_memory(int estimated_tokens) const;
    
    // 执行 memory flush（静默 agent turn）
    void flush_memory(const std::vector<Message>& recent_messages);
    
private:
    int last_flush_compaction_count_ = -1;  // 防止重复 flush
};

// agent_loop.cpp 修改
void AgentLoop::process_message_stream(...) {
    // 在每次 LLM 调用前检查
    if (memory_manager_->should_flush_memory(estimated_tokens)) {
        memory_manager_->flush_memory(history);
    }
    // ... 继续正常处理
}
```

---

### 3.2 方案二：智能上下文压缩

**目标**：改进当前的整合机制，实现渐进式、安全的压缩

#### 3.2.1 Token 估算

```cpp
// 新增 token 估算函数
int estimate_tokens(const std::string& text) {
    // 简单估算：字符数 / 4（中英文混合场景）
    // 更精确的实现可以使用 tiktoken
    return static_cast<int>(text.size() / 4.0 * 1.2);  // 20% 安全边际
}

int estimate_messages_tokens(const std::vector<Message>& messages) {
    int total = 0;
    for (const auto& msg : messages) {
        total += estimate_tokens(msg.content_to_string());
        for (const auto& tc : msg.tool_calls) {
            total += estimate_tokens(tc.function_name);
            total += estimate_tokens(tc.function_arguments);
        }
    }
    return static_cast<int>(total * 1.2);  // SAFETY_MARGIN
}
```

#### 3.2.2 分块压缩算法

```cpp
// 将大量消息分成多个 chunk 分别压缩
std::vector<std::vector<Message>> chunk_messages_by_tokens(
    const std::vector<Message>& messages,
    int max_tokens_per_chunk
) {
    std::vector<std::vector<Message>> chunks;
    std::vector<Message> current_chunk;
    int current_tokens = 0;
    
    const int EFFECTIVE_MAX = max_tokens_per_chunk / 1.2;  // 安全边际
    
    for (const auto& msg : messages) {
        int msg_tokens = estimate_messages_tokens({msg});
        
        if (!current_chunk.empty() && 
            current_tokens + msg_tokens > EFFECTIVE_MAX) {
            chunks.push_back(std::move(current_chunk));
            current_chunk.clear();
            current_tokens = 0;
        }
        
        current_chunk.push_back(msg);
        current_tokens += msg_tokens;
    }
    
    if (!current_chunk.empty()) {
        chunks.push_back(std::move(current_chunk));
    }
    
    return chunks;
}
```

#### 3.2.3 渐进式降级策略

```cpp
enum class CompactionResult {
    Success,           // 完整压缩成功
    PartialSuccess,    // 部分压缩（排除超大消息）
    Fallback,          // 降级：仅记录元信息
    Failed             // 完全失败
};

CompactionResult compact_with_fallback(
    const std::vector<Message>& messages,
    int context_window
) {
    // 尝试 1：完整压缩
    try {
        return try_full_compaction(messages);
    } catch (...) {}
    
    // 尝试 2：排除超大消息
    auto small_messages = filter_oversized(messages, context_window);
    if (!small_messages.empty()) {
        try {
            return try_partial_compaction(small_messages);
        } catch (...) {}
    }
    
    // 尝试 3：仅记录元信息
    return create_metadata_summary(messages);
}
```

#### 3.2.4 标识符保留指令

**整合提示词增强**：
```
You are a memory consolidation agent. Your task is to summarize 
the conversation while preserving critical information.

CRITICAL REQUIREMENTS:
1. Preserve ALL opaque identifiers EXACTLY as written:
   - UUIDs, hashes, file paths, URLs
   - API keys, tokens, session IDs
   - Hostnames, IP addresses, ports
   - Error codes and exact error messages

2. Preserve ALL decisions and their rationale:
   - What was decided and why
   - Who made the decision
   - Any constraints or trade-offs

3. Preserve ALL pending tasks:
   - TODOs, reminders, follow-ups
   - Dependencies and blockers

4. Use [YYYY-MM-DD HH:MM] timestamps for all entries

Call the save_memory tool with your consolidation.
```

---

### 3.3 方案三：数据库 Schema 优化

#### 3.3.1 新增表结构

```sql
-- 压缩记录表
CREATE TABLE IF NOT EXISTS compactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    summary TEXT NOT NULL,
    first_kept_message_id INTEGER,  -- 第一个保留的消息 ID
    tokens_before INTEGER,          -- 压缩前 token 数
    tokens_after INTEGER,           -- 压缩后 token 数
    created_at TEXT NOT NULL,
    
    FOREIGN KEY (first_kept_message_id) REFERENCES messages(id)
);

CREATE INDEX IF NOT EXISTS idx_compactions_session 
ON compactions(session_id);

-- Token 统计表（用于快速查询）
CREATE TABLE IF NOT EXISTS token_stats (
    session_id TEXT PRIMARY KEY,
    total_tokens INTEGER DEFAULT 0,
    last_updated TEXT NOT NULL
);

-- 消息表增加 token 字段
ALTER TABLE messages ADD COLUMN token_count INTEGER DEFAULT 0;
ALTER TABLE messages ADD COLUMN consolidated INTEGER DEFAULT 0;
```

#### 3.3.2 FTS5 全文搜索

```sql
-- 启用 SQLite FTS5 扩展
-- 创建全文搜索虚拟表
CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
    content,
    role,
    timestamp,
    content='messages',
    content_rowid='id',
    tokenize='unicode61'  -- 支持中文
);

-- 触发器：自动同步
CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
    INSERT INTO messages_fts(rowid, content, role, timestamp)
    VALUES (new.id, new.content, new.role, new.timestamp);
END;

CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
    INSERT INTO messages_fts(messages_fts, rowid, content, role, timestamp)
    VALUES ('delete', old.id, old.content, old.role, old.timestamp);
END;

CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN
    INSERT INTO messages_fts(messages_fts, rowid, content, role, timestamp)
    VALUES ('delete', old.id, old.content, old.role, old.timestamp);
    INSERT INTO messages_fts(rowid, content, role, timestamp)
    VALUES (new.id, new.content, new.role, new.timestamp);
END;
```

---

### 3.4 方案四：上下文构建优化

#### 3.4.1 分层上下文结构

```cpp
struct ContextBudget {
    int context_window;          // 模型上下文窗口 (128k)
    int system_prompt_max;       // 系统提示词上限 (8k)
    int summary_max;             // 历史摘要上限 (20k)
    int recent_messages_max;     // 最近消息上限 (40k)
    int tool_results_max;        // 工具结果上限 (20k)
    int generation_reserve;      // 生成保留 (40k)
};

std::vector<Message> build_optimized_context(
    const std::vector<Message>& all_messages,
    const ContextBudget& budget
) {
    std::vector<Message> context;
    int current_tokens = 0;
    
    // 1. 添加最新压缩摘要
    auto summary = get_latest_compaction_summary();
    if (summary && current_tokens + summary.tokens <= budget.summary_max) {
        context.push_back(summary.to_message());
        current_tokens += summary.tokens;
    }
    
    // 2. 添加最近消息（保留原始格式）
    auto recent = get_recent_messages(budget.recent_messages_max);
    for (auto it = recent.rbegin(); it != recent.rend(); ++it) {
        int msg_tokens = estimate_messages_tokens({*it});
        if (current_tokens + msg_tokens > budget.recent_messages_max) {
            break;
        }
        context.insert(context.begin(), *it);
        current_tokens += msg_tokens;
    }
    
    return context;
}
```

#### 3.4.2 工具结果裁剪

```cpp
// 裁剪过长的工具结果
std::string prune_tool_result(const std::string& result, int max_chars = 10000) {
    if (result.size() <= max_chars) {
        return result;
    }
    
    // 保留前 2/3 和后 1/3
    int keep_front = max_chars * 2 / 3;
    int keep_back = max_chars / 3;
    
    std::string pruned = result.substr(0, keep_front);
    pruned += "\n\n... [truncated " + 
              std::to_string(result.size() - max_chars) + 
              " characters] ...\n\n";
    pruned += result.substr(result.size() - keep_back);
    
    return pruned;
}
```

---

## 四、配置结构更新

### 4.1 新增配置类型

```cpp
// config.hpp
struct CompactionConfig {
    // 模式选择
    enum class Mode { Default, Safeguard };
    Mode mode = Mode::Safeguard;
    
    // Token 预算
    int context_window_tokens = 128000;
    int reserve_tokens = 4000;
    int reserve_tokens_floor = 20000;
    int keep_recent_tokens = 8000;
    double max_history_share = 0.5;  // 历史占上下文的最大比例
    
    // 标识符保留策略
    enum class IdentifierPolicy { Strict, Off, Custom };
    IdentifierPolicy identifier_policy = IdentifierPolicy::Strict;
    std::string identifier_instructions;
    
    // Memory Flush
    struct MemoryFlush {
        bool enabled = true;
        int soft_threshold_tokens = 4000;
        std::string prompt;
        std::string system_prompt;
    } memory_flush;
    
    // 压缩策略
    int max_chunk_tokens = 32000;
    int min_messages_for_split = 4;
    int chunk_parts = 2;
};

struct AgentConfig {
    // ... 现有配置 ...
    
    // 新增
    CompactionConfig compaction;
};
```

### 4.2 JSON 配置示例

```json
{
  "agent": {
    "model": "qwen-max",
    "max_iterations": 15,
    "temperature": 0.7,
    "max_tokens": 4096,
    
    "compaction": {
      "mode": "safeguard",
      "context_window_tokens": 128000,
      "reserve_tokens_floor": 20000,
      "keep_recent_tokens": 8000,
      "max_history_share": 0.5,
      "identifier_policy": "strict",
      
      "memory_flush": {
        "enabled": true,
        "soft_threshold_tokens": 4000
      },
      
      "max_chunk_tokens": 32000,
      "min_messages_for_split": 4
    }
  }
}
```

---

## 五、实现优先级

### P0 - 必须实现（2周）

| 任务 | 工作量 | 价值 |
|------|--------|------|
| Token 估算函数 | 2h | 基础能力 |
| 标识符保留指令 | 1h | 防止信息丢失 |
| Memory Flush 机制 | 8h | 防止信息丢失 |
| 上下文预算管理 | 4h | 精确控制 |

### P1 - 重要功能（3周）

| 任务 | 工作量 | 价值 |
|------|--------|------|
| 分块压缩算法 | 8h | 处理长对话 |
| 渐进式降级 | 4h | 提高可靠性 |
| FTS5 全文搜索 | 4h | 搜索性能 |
| 工具结果裁剪 | 2h | 减少冗余 |

### P2 - 优化增强（2周）

| 任务 | 工作量 | 价值 |
|------|--------|------|
| 向量搜索支持 | 16h | 语义检索 |
| 时序衰减 | 4h | 提高相关性 |
| MMR 多样性 | 4h | 减少重复 |
| 混合搜索 | 8h | 最佳效果 |

---

## 六、测试计划

### 6.1 单元测试

```cpp
// 测试 token 估算
TEST_CASE("Token estimation") {
    CHECK(estimate_tokens("Hello world") == Approx(3).margin(1));
    CHECK(estimate_tokens("你好世界") == Approx(4).margin(1));
}

// 测试分块算法
TEST_CASE("Message chunking") {
    std::vector<Message> messages = create_test_messages(100);
    auto chunks = chunk_messages_by_tokens(messages, 8000);
    
    for (const auto& chunk : chunks) {
        CHECK(estimate_messages_tokens(chunk) <= 8000 * 1.2);
    }
}

// 测试 memory flush 触发
TEST_CASE("Memory flush trigger") {
    MemoryManager mgr(test_workspace);
    
    CHECK_FALSE(mgr.should_flush_memory(100000, 128000, 20000, 4000));
    CHECK(mgr.should_flush_memory(105000, 128000, 20000, 4000));
}
```

### 6.2 集成测试

```cpp
// 测试完整压缩流程
TEST_CASE("Full compaction flow") {
    auto agent = create_test_agent();
    
    // 模拟长对话
    for (int i = 0; i < 200; i++) {
        agent->chat("Message " + std::to_string(i));
    }
    
    // 验证压缩触发
    CHECK(agent->get_compaction_count() > 0);
    
    // 验证上下文大小
    CHECK(agent->get_context_tokens() < 128000 * 0.6);
    
    // 验证信息保留
    auto summary = agent->get_latest_summary();
    CHECK(summary.has_value());
    CHECK_FALSE(summary->summary.empty());
}
```

---

## 七、监控指标

### 7.1 关键指标

```cpp
struct MemoryMetrics {
    // 存储指标
    int64_t total_messages;
    int64_t total_bytes;
    int64_t consolidated_messages;
    
    // Token 指标
    int context_tokens;
    int summary_tokens;
    int recent_messages_tokens;
    
    // 压缩指标
    int compaction_count;
    int flush_count;
    double compression_ratio;  // tokens_after / tokens_before
    
    // 性能指标
    std::chrono::milliseconds last_compaction_duration;
    std::chrono::milliseconds last_search_duration;
};
```

### 7.2 日志格式

```
[INFO] Memory flush triggered: 105000 tokens (threshold: 104000)
[INFO] Memory flush completed: saved 3 entries to daily_memory
[INFO] Compaction triggered: 120000 tokens
[INFO] Compaction completed: 120000 -> 15000 tokens (87.5% reduction)
[WARN] Compaction fallback: partial summary (2 oversized messages excluded)
```

---

## 八、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Token 估算不准确 | 压缩过早/过晚 | 使用 20% 安全边际 |
| 关键信息丢失 | 用户体验下降 | 强制保留标识符 |
| 压缩失败 | 上下文溢出 | 三级降级策略 |
| 搜索性能下降 | 响应变慢 | FTS5 索引 + 缓存 |
| 数据库膨胀 | 存储占用 | 定期清理已压缩消息 |

---

## 九、总结

本优化方案从 OpenClaw 的成熟实践中提炼了以下核心改进：

1. **预压缩保存** - 在压缩前主动保存重要信息
2. **智能压缩** - 分块处理、渐进降级、标识符保留
3. **精确预算** - 基于 token 的上下文管理
4. **混合检索** - BM25 + 向量搜索

实施后预期效果：
- 长对话上下文减少 **70-85%**
- 关键信息保留率 **>95%**
- 搜索性能提升 **10x+**
- 存储空间节省 **50%+**
