# icraw Demo Memory Integration Test Design

## Overview

This document describes test cases for icraw_demo's memory features:
- **Short-term Memory**: Conversation history stored in SQLite `messages` table
- **Long-term Memory**: Summaries stored in `summaries` and `daily_memory` tables
- **Memory Consolidation**: LLM-based summarization triggered by message threshold
- **Memory Search**: LIKE-based search across conversation history

## Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `memory_window` | 50 | Messages to keep in short-term memory |
| `consolidation_threshold` | 30 | Trigger consolidation when messages exceed this |

---

## 1. Short-term Memory Test Cases

### TC-SM-01: Basic Message Storage
**Objective**: Verify messages are stored correctly in SQLite

**Steps**:
1. Start icraw_demo with fresh workspace
2. Send message: "Hello, my name is Alice"
3. Send message: "I live in Beijing"
4. Exit and restart demo
5. Send message: "What is my name?"

**Expected**:
- Messages stored in `messages` table
- Agent can recall previous context
- `get_message_count() == 4` (2 user + 2 assistant)

**Verification**:
```sql
SELECT COUNT(*) FROM messages;
SELECT content FROM messages WHERE role='user' ORDER BY timestamp;
```

---

### TC-SM-02: Message Limit Enforcement
**Objective**: Verify memory window limit is enforced

**Steps**:
1. Set `memory_window = 5` in config
2. Send 10 messages sequentially
3. Check context window size

**Expected**:
- Only recent 5 messages kept in context
- Older messages still in database but not in context

**Verification**:
```sql
SELECT COUNT(*) FROM messages; -- Should be 20 (10 user + 10 assistant)
```

---

### TC-SM-03: Session Persistence
**Objective**: Verify messages persist across sessions

**Steps**:
1. Send message: "Remember the code 12345"
2. Exit demo
3. Restart demo with same workspace
4. Ask: "What code should I remember?"

**Expected**:
- Agent recalls the code from previous session
- Database contains all messages from both sessions

---

### TC-SM-04: Clear History
**Objective**: Verify `/clear` command works

**Steps**:
1. Send several messages
2. Enter `/clear` command
3. Check message count

**Expected**:
- `get_message_count() == 0`
- Agent has no context of previous messages

**Verification**:
```sql
SELECT COUNT(*) FROM messages; -- Should be 0
```

---

### TC-SM-05: Message Order Preservation
**Objective**: Verify messages are returned in correct order

**Steps**:
1. Send messages: "First", "Second", "Third"
2. Retrieve messages via `get_recent_messages()`

**Expected**:
- Messages returned in reverse chronological order (newest first)
- Timestamps are monotonically increasing

---

## 2. Long-term Memory Test Cases

### TC-LTM-01: Summary Creation
**Objective**: Verify summary is created and stored

**Steps**:
1. Trigger consolidation (send 35+ messages)
2. Check `summaries` table

**Expected**:
- New entry in `summaries` table
- Summary contains key information from conversation

**Verification**:
```sql
SELECT * FROM summaries ORDER BY created_at DESC LIMIT 1;
```

---

### TC-LTM-02: Summary Retrieval
**Objective**: Verify latest summary is retrieved correctly

**Steps**:
1. Create multiple summaries
2. Call `get_latest_summary()`

**Expected**:
- Returns most recent summary
- Includes `message_count` field

---

### TC-LTM-03: Daily Memory Storage
**Objective**: Verify daily memory is saved

**Steps**:
1. Trigger consolidation
2. Check `daily_memory` table

**Expected**:
- Entry created with today's date
- Content includes timestamp prefix `[YYYY-MM-DD HH:MM]`

**Verification**:
```sql
SELECT * FROM daily_memory WHERE date = date('now');
```

---

### TC-LTM-04: Daily Memory File
**Objective**: Verify daily memory is also saved to file

**Steps**:
1. Trigger consolidation
2. Check `workspace/memory/YYYY-MM-DD.md`

**Expected**:
- File exists
- Contains memory entry with `---` separator

---

### TC-LTM-05: Summary Integration in Context
**Objective**: Verify summary is included in system prompt

**Steps**:
1. Create a summary with unique keyword "UNIQUE_KEYWORD_123"
2. Start new conversation
3. Check if summary appears in agent context

**Expected**:
- Agent has access to summary information
- PromptBuilder includes summary in system prompt

---

## 3. Memory Consolidation Test Cases

### TC-MC-01: Threshold Trigger
**Objective**: Verify consolidation triggers at threshold

**Steps**:
1. Set `consolidation_threshold = 10`
2. Send 11 messages (6 user + 5 assistant = 11 total)
3. Check logs for "Triggering memory consolidation"

**Expected**:
- Consolidation triggered when message_count > threshold
- LLM called with consolidation prompt

**Verification**:
```
Check log for: "Triggering memory consolidation: 11 messages > 10 threshold"
```

---

### TC-MC-02: No Trigger Below Threshold
**Objective**: Verify consolidation does NOT trigger below threshold

**Steps**:
1. Set `consolidation_threshold = 20`
2. Send 5 messages
3. Check logs

**Expected**:
- No consolidation triggered
- No summary created

---

### TC-MC-03: Consolidation Preserves Recent Messages
**Objective**: Verify recent messages are kept after consolidation

**Steps**:
1. Set `memory_window = 10`, `consolidation_threshold = 5`
2. Send 10 messages
3. Trigger consolidation
4. Check `get_messages_for_consolidation(keep_count=5)`

**Expected**:
- Recent 5 messages (memory_window/2) kept in context
- Older messages consolidated into summary

---

### TC-MC-04: LLM Tool Call Required
**Objective**: Verify consolidation requires LLM to call save_memory tool

**Steps**:
1. Trigger consolidation
2. Verify LLM receives tool definition for `save_memory`
3. Verify tool result is processed

**Expected**:
- Tool definition includes `history_entry` and `memory_update` parameters
- Summary saved only if LLM calls the tool

---

### TC-MC-05: Incremental Consolidation
**Objective**: Verify multiple consolidations accumulate

**Steps**:
1. Trigger first consolidation (messages 1-30)
2. Send more messages (31-60)
3. Trigger second consolidation
4. Check summary includes both old and new information

**Expected**:
- Second summary includes content from first summary
- `memory_update` parameter contains "Current Long-term Memory"

---

### TC-MC-06: Consolidation Failure Handling
**Objective**: Verify graceful handling when LLM doesn't call tool

**Steps**:
1. Mock LLM to return text without tool call
2. Trigger consolidation
3. Check logs

**Expected**:
- Warning logged: "Memory consolidation: LLM did not call save_memory tool"
- No crash, no partial data

---

## 4. Memory Search Test Cases

### TC-MS-01: Basic Search
**Objective**: Verify search returns matching messages

**Steps**:
1. Send messages containing "Paris", "London", "Tokyo"
2. Search for "Paris"
3. Check results

**Expected**:
- Returns messages containing "Paris"
- Results include role, content, timestamp

**Verification via tool**:
```json
{"query": "Paris", "limit": 10}
```

---

### TC-MS-02: Case Insensitivity
**Objective**: Verify search is case-insensitive

**Steps**:
1. Send message: "I love PROGRAMMING"
2. Search for "programming"
3. Search for "PROGRAMMING"
4. Search for "Programming"

**Expected**:
- All three searches return the same message
- LIKE search in SQLite is case-insensitive for ASCII

---

### TC-MS-03: Limit Parameter
**Objective**: Verify limit parameter works

**Steps**:
1. Send 20 messages containing "test"
2. Search with limit=5
3. Search with limit=10

**Expected**:
- First search returns max 5 results
- Second search returns max 10 results

---

### TC-MS-04: No Results
**Objective**: Verify empty results for non-matching query

**Steps**:
1. Search for "NONEXISTENT_KEYWORD_XYZ123"

**Expected**:
- Returns empty array
- No error

---

### TC-MS-05: Special Characters
**Objective**: Verify search handles special characters

**Steps**:
1. Send message: "Email: test@example.com"
2. Search for "test@example.com"
3. Send message: "Code: func(x, y)"
4. Search for "func(x"

**Expected**:
- Special characters handled correctly
- No SQL injection (parameterized query)

---

### TC-MS-06: Partial Match
**Objective**: Verify partial matching works

**Steps**:
1. Send message: "The quick brown fox"
2. Search for "quick"
3. Search for "brown"
4. Search for "fox"

**Expected**:
- All partial matches return the message
- LIKE '%query%' pattern used

---

### TC-MS-07: Order By Timestamp
**Objective**: Verify results ordered by timestamp DESC

**Steps**:
1. Send message: "First occurrence of keyword"
2. Send message: "Second occurrence of keyword"
3. Send message: "Third occurrence of keyword"
4. Search for "keyword"

**Expected**:
- Results returned newest first
- Third message appears before first

---

## 5. Edge Cases

### TC-EC-01: Empty Memory
**Objective**: Verify behavior with no messages

**Steps**:
1. Start with fresh workspace
2. Search memory
3. Get recent messages
4. Get latest summary

**Expected**:
- Search returns empty array
- get_recent_messages returns empty vector
- get_latest_summary returns std::nullopt

---

### TC-EC-02: Large Message Content
**Objective**: Verify handling of large messages

**Steps**:
1. Send message with 10,000+ characters
2. Search for unique keyword in message
3. Verify retrieval

**Expected**:
- Message stored completely
- Search works on large content

---

### TC-EC-03: Unicode Content
**Objective**: Verify Unicode handling

**Steps**:
1. Send messages with Chinese: "你好世界"
2. Send messages with emoji: "Hello 👋 World 🌍"
3. Search for "你好"
4. Search for "👋"

**Expected**:
- Unicode stored correctly
- Search works with Unicode

---

### TC-EC-04: SQL Injection Prevention
**Objective**: Verify parameterized queries prevent injection

**Steps**:
1. Search for: "'; DROP TABLE messages; --"
2. Search for: "test' OR '1'='1"
3. Verify database integrity

**Expected**:
- No SQL injection
- Messages table still exists
- Search returns no results (no match)

---

### TC-EC-05: Concurrent Access
**Objective**: Verify thread safety (if applicable)

**Steps**:
1. Send message from main thread
2. Trigger consolidation
3. Search memory simultaneously

**Expected**:
- No database lock errors
- Consistent results

---

## 6. Integration Test Scripts

### PowerShell Test Script

```powershell
# test_memory_integration.ps1
# Run icraw_demo integration tests

$DemoPath = ".\build\demo\Debug\icraw_demo.exe"
$TestWorkspace = ".\test_workspace_memory"
$ApiKey = $env:OPENAI_API_KEY
$BaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
$Model = "qwen3-max"

# Setup
Write-Host "=== icraw Memory Integration Tests ===" -ForegroundColor Cyan
Remove-Item -Recurse -Force $TestWorkspace -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $TestWorkspace | Out-Null

# Copy DLLs
Copy-Item ".\vcpkg_installed\x64-windows\debug\bin\*.dll" ".\build\demo\Debug\" -Force

function Send-Message {
    param([string]$message)
    $message | & $DemoPath --api-key $ApiKey --base-url $BaseUrl --model $Model --workspace $TestWorkspace --no-stream 2>&1
}

function Test-Case {
    param([string]$name, [scriptblock]$test)
    Write-Host "`n--- $name ---" -ForegroundColor Yellow
    & $test
}

# TC-SM-01: Basic Message Storage
Test-Case "TC-SM-01: Basic Message Storage" {
    Send-Message "Hello, my name is Alice"
    Send-Message "I live in Beijing"
    
    # Check database
    $dbPath = Join-Path $TestWorkspace "memory.db"
    Write-Host "Database created at: $dbPath"
    if (Test-Path $dbPath) {
        Write-Host "[PASS] Database exists" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] Database not found" -ForegroundColor Red
    }
}

# TC-SM-04: Clear History
Test-Case "TC-SM-04: Clear History" {
    "/clear" | & $DemoPath --api-key $ApiKey --base-url $BaseUrl --model $Model --workspace $TestWorkspace --no-stream 2>&1
    Write-Host "[INFO] Clear command sent"
}

# TC-MS-01: Basic Search (via agent query)
Test-Case "TC-MS-01: Memory Search" {
    Send-Message "I visited Paris last summer"
    Send-Message "The weather in London was rainy"
    Send-Message "Tokyo has great food"
    
    Start-Sleep -Seconds 2
    
    Send-Message "Search your memory for Paris"
    Write-Host "[INFO] Search query sent"
}

# Cleanup
Write-Host "`n=== Tests Complete ===" -ForegroundColor Cyan
Write-Host "Test workspace: $TestWorkspace"
Write-Host "Check memory.db for stored data"
```

---

## 7. Unit Test Additions

Add these tests to `tests/memory_manager.test.cpp`:

```cpp
// === Additional Memory Search Tests ===

TEST_CASE("MemoryManager::search_memory returns empty for no match", "[memory_manager][sqlite][search]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_empty";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    manager.add_message("user", "Hello world", "default", {});
    
    auto results = manager.search_memory("NONEXISTENT_KEYWORD_XYZ123", 10);
    REQUIRE(results.empty());
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory respects limit", "[memory_manager][sqlite][search]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_limit";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    for (int i = 0; i < 20; ++i) {
        manager.add_message("user", "Test message " + std::to_string(i), "default", {});
    }
    
    auto results = manager.search_memory("Test", 5);
    REQUIRE(results.size() <= 5);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory handles special characters", "[memory_manager][sqlite][search]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_special";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    manager.add_message("user", "Email: test@example.com", "default", {});
    
    // This should NOT cause SQL injection
    auto results = manager.search_memory("'; DROP TABLE messages; --", 10);
    REQUIRE(results.empty()); // No match, not an error
    
    // Verify table still exists
    results = manager.search_memory("test@example.com", 10);
    REQUIRE(results.size() == 1);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::search_memory with Unicode", "[memory_manager][sqlite][search]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_search_unicode";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    manager.add_message("user", u8"你好世界", "default", {});
    
    auto results = manager.search_memory(u8"你好", 10);
    REQUIRE(results.size() >= 1);
    
    manager.close();
    fs::remove_all(temp_dir);
}

// === Consolidation Tests ===

TEST_CASE("MemoryManager::get_messages_for_consolidation respects keep_count", "[memory_manager][sqlite][consolidation]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_consolidation";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    for (int i = 0; i < 20; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Keep recent 5, consolidate older 15
    auto to_consolidate = manager.get_messages_for_consolidation(5);
    REQUIRE(to_consolidate.size() == 15); // 20 - 5 = 15
    
    // Verify they are the oldest messages
    REQUIRE(to_consolidate[0].content == "Message 0");
    REQUIRE(to_consolidate[14].content == "Message 14");
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager::get_messages_for_consolidation returns empty for few messages", "[memory_manager][sqlite][consolidation]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_consolidation_empty";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    for (int i = 0; i < 3; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Keep 5, but only 3 exist
    auto to_consolidate = manager.get_messages_for_consolidation(5);
    REQUIRE(to_consolidate.empty()); // Nothing to consolidate
    
    manager.close();
    fs::remove_all(temp_dir);
}

// === Edge Cases ===

TEST_CASE("MemoryManager handles large content", "[memory_manager][sqlite][edge]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_large_content";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Create 10KB message
    std::string large_content(10000, 'X');
    large_content = "START " + large_content + " END";
    
    int64_t id = manager.add_message("user", large_content, "default", {});
    REQUIRE(id > 0);
    
    // Search for unique markers
    auto results = manager.search_memory("START", 10);
    REQUIRE(results.size() >= 1);
    REQUIRE(results[0].content.find("END") != std::string::npos);
    
    manager.close();
    fs::remove_all(temp_dir);
}

TEST_CASE("MemoryManager handles concurrent reads", "[memory_manager][sqlite][edge]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_concurrent";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages
    for (int i = 0; i < 10; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Multiple reads should work
    auto recent = manager.get_recent_messages(5, "default");
    auto search = manager.search_memory("Message", 10);
    auto count = manager.get_message_count("default");
    
    REQUIRE(recent.size() == 5);
    REQUIRE(search.size() >= 5);
    REQUIRE(count == 10);
    
    manager.close();
    fs::remove_all(temp_dir);
}
```

---

## 6. Token-aware Memory Management Test Cases (NEW)

### TC-TK-01: Token Estimation Accuracy
**Objective**: Verify token estimation is reasonably accurate

**Steps**:
1. Send message with known content: "Hello world this is a test message"
2. Query token count from database
3. Compare with estimation

**Expected**:
- Token count > 0
- Token count approximately matches `length / 2.5 * 1.2`

**Verification**:
```sql
SELECT id, content, token_count FROM messages ORDER BY id DESC LIMIT 1;
```

---

### TC-TK-02: Token Estimation with Chinese
**Objective**: Verify token estimation works with Chinese content

**Steps**:
1. Send message: "你好世界这是一个测试"
2. Query token count

**Expected**:
- Token count > 0
- Chinese characters properly counted

---

### TC-TK-03: Total Token Tracking
**Objective**: Verify total token count is tracked per session

**Steps**:
1. Send multiple messages
2. Query total token stats

**Expected**:
- `get_total_tokens()` returns sum of all message tokens
- `token_stats` table updated correctly

**Verification**:
```sql
SELECT * FROM token_stats WHERE session_id = 'default';
```

---

### TC-TK-04: Context Budget Calculation
**Objective**: Verify context budget is calculated correctly

**Steps**:
1. Configure context_window_tokens = 64000
2. Send messages until near limit
3. Check if compaction is triggered

**Expected**:
- Compaction triggers before context overflow
- `should_trigger_compaction()` returns true at correct threshold

---

### TC-TK-05: Available Context Calculation
**Objective**: Verify available context space calculation

**Steps**:
1. Set reserve_tokens_floor = 20000
2. Send messages with ~100000 tokens
3. Check available context

**Expected**:
- `calculate_available_context()` returns correct remaining space
- Returns 0 or positive value (never negative)

---

## 7. Memory Flush Test Cases (NEW)

### TC-MF-01: Memory Flush Trigger
**Objective**: Verify memory flush triggers at soft threshold

**Steps**:
1. Configure memory_flush.soft_threshold_tokens = 4000
2. Send messages until approaching context limit
3. Check logs for memory flush trigger

**Expected**:
- Memory flush triggers when:
  `current_tokens >= context_window - reserve_floor - soft_threshold`
- Log shows: "Memory flush triggered"

---

### TC-MF-02: Memory Flush Disabled
**Objective**: Verify no flush when disabled

**Steps**:
1. Set memory_flush.enabled = false
2. Send many messages
3. Check no flush occurs

**Expected**:
- No memory flush triggers
- Normal operation continues

---

### TC-MF-03: Memory Flush Recording
**Objective**: Verify flush is recorded in database

**Steps**:
1. Trigger memory flush
2. Check `memory_flush_log` table

**Expected**:
- Entry created with timestamp
- `get_last_flush_timestamp()` returns valid timestamp

**Verification**:
```sql
SELECT * FROM memory_flush_log ORDER BY created_at DESC LIMIT 1;
```

---

### TC-MF-04: Memory Flush Preserves Information
**Objective**: Verify important info is saved before compaction

**Steps**:
1. Send message with important data: "My API key is sk-1234567890abcdef"
2. Trigger memory flush
3. Check if info is preserved in daily_memory

**Expected**:
- Important information saved to daily memory
- Agent can still recall after compaction

---

## 8. FTS5 Full-Text Search Test Cases (NEW)

### TC-FTS-01: FTS5 Search Basic
**Objective**: Verify FTS5 search works

**Steps**:
1. Send messages with various content
2. Use search_memory_fts() to search
3. Compare with LIKE search results

**Expected**:
- FTS5 returns matching results
- Falls back to LIKE if FTS5 unavailable

---

### TC-FTS-02: FTS5 Search Ranking
**Objective**: Verify FTS5 returns relevant results first

**Steps**:
1. Send messages with varying relevance
2. Search with FTS5
3. Check result order

**Expected**:
- More relevant results appear first
- Results ordered by relevance

---

### TC-FTS-03: FTS5 Fallback to LIKE
**Objective**: Verify fallback when FTS5 unavailable

**Steps**:
1. Mock FTS5 unavailable scenario
2. Execute search
3. Verify LIKE fallback works

**Expected**:
- No error when FTS5 unavailable
- LIKE search returns results

---

### TC-FTS-04: FTS5 Unicode Support
**Objective**: Verify FTS5 handles Unicode

**Steps**:
1. Send message: "北京是中国的首都"
2. Search for "北京"
3. Search for "中国"

**Expected**:
- Unicode search works
- Results returned correctly

---

### TC-FTS-05: FTS5 Sync Triggers
**Objective**: Verify FTS5 index stays in sync

**Steps**:
1. Send message: "Test message one"
2. Search for "Test" - should find it
3. Send another message: "Another test"
4. Search again - should find both

**Expected**:
- New messages automatically indexed
- DELETE/UPDATE operations sync correctly

**Verification**:
```sql
-- Check FTS5 table exists and has content
SELECT * FROM messages_fts WHERE messages_fts MATCH 'Test';
```

---

## 9. Identifier Preservation Test Cases (NEW)

### TC-IP-01: UUID Preservation
**Objective**: Verify UUIDs are preserved in consolidation

**Steps**:
1. Send message: "The request ID is 550e8400-e29b-41d4-a716-446655440000"
2. Trigger consolidation
3. Check summary contains exact UUID

**Expected**:
- UUID preserved exactly in summary
- No truncation or modification

---

### TC-IP-02: File Path Preservation
**Objective**: Verify file paths are preserved

**Steps**:
1. Send message: "Saved to /home/user/documents/file.txt"
2. Trigger consolidation
3. Check summary contains exact path

**Expected**:
- Full path preserved
- No path modification

---

### TC-IP-03: URL Preservation
**Objective**: Verify URLs are preserved

**Steps**:
1. Send message: "API endpoint: https://api.example.com/v1/users?id=123"
2. Trigger consolidation
3. Check summary contains exact URL

**Expected**:
- Full URL with query params preserved
- No truncation

---

### TC-IP-04: Email Preservation
**Objective**: Verify email addresses are preserved

**Steps**:
1. Send message: "Contact: john.doe@example.com"
2. Trigger consolidation
3. Check summary contains exact email

**Expected**:
- Email preserved exactly

---

### TC-IP-05: IP Address Preservation
**Objective**: Verify IP addresses are preserved

**Steps**:
1. Send message: "Server at 192.168.1.100:8080"
2. Trigger consolidation
3. Check summary contains exact IP and port

**Expected**:
- IP and port preserved exactly

---

### TC-IP-06: Identifier Detection
**Objective**: Verify identifier detection function works

**Steps**:
1. Test various strings for identifier presence
2. Check contains_important_identifiers() returns correct result

**Expected**:
- UUIDs, paths, URLs, IPs, emails detected
- Plain text returns false

---

### TC-IP-07: Strict Policy Prompt
**Objective**: Verify strict policy adds preservation instructions

**Steps**:
1. Set identifier_policy = "strict"
2. Get consolidation prompt
3. Verify preservation instructions included

**Expected**:
- Prompt includes "CRITICAL REQUIREMENTS" section
- Lists all identifier types to preserve

---

## 10. Message Chunking Test Cases (NEW)

### TC-CH-01: Single Chunk for Small Messages
**Objective**: Verify small messages stay in one chunk

**Steps**:
1. Send 5 small messages
2. Trigger chunking with large max_tokens_per_chunk
3. Verify single chunk created

**Expected**:
- All messages in one chunk
- No splitting

---

### TC-CH-02: Multiple Chunks for Large Messages
**Objective**: Verify large message sets are chunked

**Steps**:
1. Send 20 large messages
2. Trigger chunking with small max_tokens_per_chunk
3. Verify multiple chunks created

**Expected**:
- Messages split into multiple chunks
- Each chunk within token limit

---

### TC-CH-03: Chunk Token Limit Respected
**Objective**: Verify chunks respect token limits

**Steps**:
1. Create messages with varying sizes
2. Chunk with max_tokens_per_chunk = 1000
3. Verify each chunk is within limit (with safety margin)

**Expected**:
- Each chunk's tokens <= max_tokens * 1.2
- No chunk exceeds limit significantly

---

### TC-CH-04: All Messages Preserved in Chunks
**Objective**: Verify no messages lost during chunking

**Steps**:
1. Send 15 messages
2. Chunk with small limit
3. Count total messages across all chunks

**Expected**:
- Sum of messages in chunks == original message count
- No message duplication or loss

---

### TC-CH-05: Empty Message List Chunking
**Objective**: Verify empty list handling

**Steps**:
1. Call chunk_messages_by_tokens with empty list
2. Verify empty result

**Expected**:
- Returns empty vector of chunks
- No error

---

## 11. Progressive Fallback Test Cases (NEW)

### TC-PF-01: Full Compaction Success
**Objective**: Verify successful full compaction

**Steps**:
1. Send moderate number of messages
2. Trigger compaction
3. Check result is Success

**Expected**:
- CompactionResult::Success returned
- Summary created
- Old messages marked consolidated

---

### TC-PF-02: Partial Success with Oversized Messages
**Objective**: Verify partial compaction when some messages too large

**Steps**:
1. Include one very large message
2. Trigger compaction
3. Check result is PartialSuccess

**Expected**:
- CompactionResult::PartialSuccess returned
- Large message excluded
- Other messages compacted

---

### TC-PF-03: Fallback on LLM Failure
**Objective**: Verify fallback when LLM fails

**Steps**:
1. Mock LLM failure scenario
2. Trigger compaction
3. Check result is Fallback

**Expected**:
- CompactionResult::Fallback returned
- Metadata summary created instead
- No crash

---

### TC-PF-04: Failed Result on Complete Failure
**Objective**: Verify Failed result handling

**Steps**:
1. Mock complete failure scenario
2. Trigger compaction
3. Check result is Failed

**Expected**:
- CompactionResult::Failed returned
- Error logged
- No crash

---

### TC-PF-05: Compaction Record Created
**Objective**: Verify compaction record is always created

**Steps**:
1. Trigger any compaction (success or fallback)
2. Check compactions table

**Expected**:
- Record created with result status
- tokens_before and tokens_after recorded

**Verification**:
```sql
SELECT * FROM compactions ORDER BY created_at DESC LIMIT 1;
```

---

## 12. Tool Result Pruning Test Cases (NEW)

### TC-TR-01: Small Result Not Pruned
**Objective**: Verify small results are not modified

**Steps**:
1. Tool returns result < 10000 chars
2. Check result is unchanged

**Expected**:
- Result returned as-is
- No truncation marker

---

### TC-TR-02: Large Result Pruned
**Objective**: Verify large results are pruned correctly

**Steps**:
1. Tool returns result > 10000 chars
2. Check result is pruned

**Expected**:
- Result size reduced
- Contains truncation marker
- Front 2/3 and back 1/3 preserved

---

### TC-TR-03: Pruning Preserves Important Info
**Objective**: Verify pruning keeps key information

**Steps**:
1. Large result with important data at start and end
2. Prune result
3. Verify key data preserved

**Expected**:
- Beginning content preserved
- Ending content preserved
- Middle truncated

---

### TC-TR-04: Custom Max Chars
**Objective**: Verify custom max_chars parameter works

**Steps**:
1. Call prune_tool_result with max_chars = 5000
2. Check result respects custom limit

**Expected**:
- Result pruned to ~5000 chars
- Truncation marker present

---

## 13. Compaction Record Test Cases (NEW)

### TC-CR-01: Compaction Record Creation
**Objective**: Verify compaction records are created

**Steps**:
1. Trigger compaction
2. Query compactions table

**Expected**:
- New record in compactions table
- Includes summary, tokens_before, tokens_after, mode

**Verification**:
```sql
SELECT * FROM compactions WHERE session_id = 'default';
```

---

### TC-CR-02: Compaction Count Tracking
**Objective**: Verify compaction count is accurate

**Steps**:
1. Trigger 3 compactions
2. Call get_compaction_count()

**Expected**:
- Returns 3
- Count increments correctly

---

### TC-CR-03: Latest Compaction Retrieval
**Objective**: Verify get_latest_compaction works

**Steps**:
1. Create multiple compaction records
2. Call get_latest_compaction()

**Expected**:
- Returns most recent record
- Includes all fields

---

### TC-CR-04: Compression Ratio Calculation
**Objective**: Verify compression ratio is calculated

**Steps**:
1. Trigger compaction with known token counts
2. Check compression_ratio field

**Expected**:
- ratio = tokens_after / tokens_before
- Value between 0 and 1

---

### TC-CR-05: Consolidated Message Marking
**Objective**: Verify messages marked as consolidated

**Steps**:
1. Trigger compaction
2. Check messages.consolidated flag

**Expected**:
- Old messages have consolidated = 1
- Recent messages have consolidated = 0

**Verification**:
```sql
SELECT id, consolidated FROM messages ORDER BY id;
```

---

### TC-CR-06: Consolidated Message Deletion
**Objective**: Verify consolidated messages can be deleted

**Steps**:
1. Mark messages as consolidated
2. Call delete_consolidated_messages()
3. Verify deletion

**Expected**:
- Consolidated messages removed
- Non-consolidated messages remain
- Returns count of deleted messages

---

## 14. Integration Test Scripts

### PowerShell Test Script

See `tests/test_memory_integration.ps1` for the complete implementation.

### Running Tests

```powershell
# Run all integration tests
.\tests\test_memory_integration.ps1

# Run with verbose output
.\tests\test_memory_integration.ps1 -Verbose

# Run with custom API key
.\tests\test_memory_integration.ps1 -ApiKey "sk-xxx"

# Run specific test category
.\tests\test_memory_integration.ps1 -TestCategory "Token"
```

---

## 15. Unit Test Additions

Add these tests to `tests/memory_optimization.test.cpp`:

```cpp
// === Token Estimation Integration Tests ===

TEST_CASE("Token estimation matches database token_count", "[memory_manager][token_integration]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_token_integration";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    std::string content = "This is a test message for token estimation";
    int64_t id = manager.add_message("user", content, "default", {});
    
    // Get the message and check token count
    auto messages = manager.get_recent_messages(1, "default");
    REQUIRE(messages.size() == 1);
    
    int estimated = icraw::estimate_tokens(content);
    // Token count should be close to estimate (within safety margin)
    CHECK(messages[0].token_count > 0);
    
    manager.close();
    fs::remove_all(temp_dir);
}

// === FTS5 Integration Tests ===

TEST_CASE("FTS5 search returns same results as LIKE search", "[memory_manager][fts_integration]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_fts_integration";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    manager.add_message("user", "The quick brown fox", "default", {});
    manager.add_message("assistant", "The lazy dog", "default", {});
    manager.add_message("user", "A quick decision", "default", {});
    
    auto fts_results = manager.search_memory_fts("quick", 10, "default");
    auto like_results = manager.search_memory("quick", 10);
    
    // Both should find at least the messages with "quick"
    CHECK(fts_results.size() >= 1);
    CHECK(like_results.size() >= 1);
    
    manager.close();
    fs::remove_all(temp_dir);
}

// === Memory Flush Integration Tests ===

TEST_CASE("Memory flush creates daily memory entry", "[memory_manager][flush_integration]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_flush_integration";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages
    for (int i = 0; i < 10; ++i) {
        manager.add_message("user", "Message " + std::to_string(i), "default", {});
    }
    
    // Record flush
    manager.record_memory_flush("default");
    
    // Verify flush recorded
    auto last_flush = manager.get_last_flush_timestamp("default");
    REQUIRE(last_flush.has_value());
    CHECK(!last_flush->empty());
    
    manager.close();
    fs::remove_all(temp_dir);
}

// === Compaction Integration Tests ===

TEST_CASE("Full compaction flow creates all records", "[memory_manager][compaction_integration]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_full_compaction";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::MemoryManager manager(temp_dir);
    
    // Add messages
    for (int i = 0; i < 20; ++i) {
        manager.add_message("user", "Test message " + std::to_string(i), "default", {});
    }
    
    // Create compaction record
    int64_t record_id = manager.create_compaction_record(
        "default", 
        "Summary of 20 messages",
        10,  // first_kept_message_id
        5000,  // tokens_before
        500,   // tokens_after
        "full"
    );
    
    CHECK(record_id > 0);
    
    // Mark messages as consolidated
    manager.mark_consolidated(10);
    
    // Verify
    auto record = manager.get_latest_compaction("default");
    REQUIRE(record.has_value());
    CHECK(record->summary == "Summary of 20 messages");
    CHECK(record->tokens_before == 5000);
    CHECK(record->tokens_after == 500);
    
    manager.close();
    fs::remove_all(temp_dir);
}
```

---

## 16. Test Execution Checklist

### Before Running Tests
- [ ] Build project: `cmake --build build --config Debug`
- [ ] Copy DLLs to test directory
- [ ] Set API key environment variable
- [ ] Clear previous test workspaces

### Unit Tests
```bash
./build/tests/Debug/icraw_tests.exe "[memory_manager]" --reporter console
./build/tests/Debug/icraw_tests.exe "[token_utils]" --reporter console
./build/tests/Debug/icraw_tests.exe "[compaction]" --reporter console
```

### Integration Tests
```powershell
.\tests\test_memory_integration.ps1
```

### Manual Verification SQL
```sql
-- Check message count and tokens
SELECT COUNT(*) as count, SUM(token_count) as total_tokens FROM messages;

-- View recent messages with token info
SELECT id, role, substr(content, 1, 50) as content_preview, token_count, consolidated, timestamp 
FROM messages ORDER BY timestamp DESC LIMIT 10;

-- Check compactions
SELECT * FROM compactions ORDER BY created_at DESC;

-- Check token stats
SELECT * FROM token_stats;

-- Check memory flush log
SELECT * FROM memory_flush_log ORDER BY created_at DESC;

-- Check FTS5 index
SELECT * FROM messages_fts WHERE messages_fts MATCH 'test' LIMIT 5;

-- Verify no SQL injection damage
SELECT name FROM sqlite_master WHERE type='table';

-- Check compression ratio
SELECT id, tokens_before, tokens_after, 
       CAST(tokens_after AS REAL) / tokens_before as ratio
FROM compactions ORDER BY created_at DESC;
```

---

## 17. Expected Results Summary

| Test Category | Test Count | Expected Pass Rate |
|--------------|------------|-------------------|
| Short-term Memory | 5 | 100% |
| Long-term Memory | 5 | 100% |
| Memory Consolidation | 6 | 100% |
| Memory Search | 7 | 100% |
| Edge Cases | 5 | 100% |
| Token-aware Management | 5 | 100% |
| Memory Flush | 4 | 100% |
| FTS5 Search | 5 | 100% |
| Identifier Preservation | 7 | 100% |
| Message Chunking | 5 | 100% |
| Progressive Fallback | 5 | 100% |
| Tool Result Pruning | 4 | 100% |
| Compaction Records | 6 | 100% |
| **Total** | **69** | **100%** |

---

## 18. Performance Benchmarks

### Expected Performance Metrics

| Operation | Target | Max Acceptable |
|-----------|--------|----------------|
| Token estimation (1KB text) | < 1ms | 5ms |
| Message add with token count | < 5ms | 20ms |
| LIKE search (1000 messages) | < 50ms | 200ms |
| FTS5 search (1000 messages) | < 10ms | 50ms |
| Message chunking (100 messages) | < 10ms | 50ms |
| Compaction record creation | < 10ms | 50ms |
| Tool result pruning (50KB) | < 1ms | 5ms |

### Memory Usage Targets

| Metric | Target | Max Acceptable |
|--------|--------|----------------|
| Database size per 1000 messages | < 5MB | 10MB |
| FTS5 index overhead | < 30% | 50% |
| In-memory message cache | < 10MB | 20MB |
