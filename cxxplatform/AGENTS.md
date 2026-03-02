# AGENTS.md - C++ Project Guidelines

This document provides essential information for AI coding agents working in this repository.

---

## Build System

### Build Commands

```bash
# Configure (one-time or after CMake changes)
cmake -B build -S .

# Build all targets
cmake --build build

# Build in release mode
cmake --build build --config Release

# Build a specific target
cmake --build build --target <target_name>

# Clean build artifacts
cmake --build build --target clean

# Full rebuild
rm -rf build && cmake -B build -S . && cmake --build build
```

### Running Tests

```bash
# Run all tests
ctest --test-dir build --output-on-failure

# Run a single test by name
ctest --test-dir build -R <test_name> --output-on-failure

# Run tests with verbose output
ctest --test-dir build -V

# Run tests in parallel
ctest --test-dir build -j$(nproc) --output-on-failure

# Run Catch2 test executable directly
./build/tests/<test_executable> [<test_spec>]
```

### Linting & Formatting

```bash
# Format code with clang-format (if configured)
find src -name "*.cpp" -o -name "*.hpp" | xargs clang-format -i

# Run clang-tidy (if configured)
cmake --build build --target tidy

# Check formatting without modifying
find src -name "*.cpp" -o -name "*.hpp" | xargs clang-format --dry-run --Werror
```

---

## Project Structure

```
.
├── CMakeLists.txt          # Main CMake configuration
├── cmake/                  # CMake modules and utilities
├── src/                    # Source files
│   ├── main.cpp           # Entry point (if executable)
│   └── <module>/          # Module-specific code
│       ├── *.hpp          # Headers
│       └── *.cpp          # Implementations
├── include/               # Public headers (for libraries)
├── tests/                 # Test files
│   ├── CMakeLists.txt    # Test configuration
│   └── *.test.cpp        # Test source files
└── third_party/          # External dependencies
```

---

## Code Style Guidelines

### C++ Standard: C++17

Use C++17 features where appropriate:
- `std::optional<T>` for nullable values
- `std::variant<Ts...>` for type-safe unions
- `std::string_view` for non-owning string references
- `if constexpr` for compile-time conditionals
- Structured bindings: `auto [x, y] = getPoint();`
- `[[nodiscard]]` attribute for functions whose return value should not be ignored

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Namespaces | `snake_case` | `namespace data_processing` |
| Classes/Structs | `PascalCase` | `class HttpRequestHandler` |
| Functions/Methods | `snake_case` | `void process_data()` |
| Variables | `snake_case` | `int item_count = 0;` |
| Constants | `SCREAMING_SNAKE_CASE` | `constexpr int MAX_RETRIES = 3;` |
| Member variables | `snake_case_` (trailing underscore) | `int buffer_size_;` |
| Template parameters | `PascalCase` | `template<typename InputIterator>` |
| Macros | `SCREAMING_SNAKE_CASE` | `#define PLATFORM_WINDOWS` |

### Header Files

```cpp
// Use #pragma once (modern, widely supported)
#pragma once

// Include order:
// 1. Related header (for .cpp files)
// 2. C system headers
// 3. C++ standard library
// 4. Third-party libraries
// 5. Project headers

// Example:
#include "my_class.hpp"           // Related header
#include <cstdio>                 // C system
#include <string>                 // C++ STL
#include <boost/asio.hpp>         // Third-party
#include "utils/logger.hpp"       // Project
```

### Modern C++ Practices

**Smart Pointers - Prefer over raw `new`/`delete`:**
```cpp
auto ptr = std::make_unique<Widget>();
auto shared = std::make_shared<Resource>();
```

**`auto` - Use judiciously:**
```cpp
// Good: type is obvious or verbose
auto result = compute_complex_type();
for (const auto& item : container) { }

// Avoid: type is unclear
auto x = get_value();  // What type is x?
```

**Range-based for loops:**
```cpp
// Prefer over index loops
for (const auto& item : container) { }

// Use iterators when index needed
for (auto it = container.begin(); it != container.end(); ++it) { }
```

**Const correctness:**
```cpp
// Const by default
void process(const std::string& input);
std::string_view get_name() const;

// Const iterators
for (auto it = vec.cbegin(); it != vec.cend(); ++it) { }
```

---

## Error Handling

### Exceptions

```cpp
// Throw by value, catch by reference
throw std::runtime_error("Failed to open file");

try {
    process_file(path);
} catch (const std::filesystem::filesystem_error& e) {
    log_error(e.what());
} catch (const std::exception& e) {
    log_error("Unexpected error: ", e.what());
}
```

### `std::optional` for Expected Failures

```cpp
// Prefer optional for recoverable "not found" cases
std::optional<User> find_user(int id) {
    if (auto it = users.find(id); it != users.end()) {
        return it->second;
    }
    return std::nullopt;
}

// Usage
if (auto user = find_user(42)) {
    std::cout << user->name << std::endl;
}
```

---

## Testing with Catch2

### Test File Structure

```cpp
// tests/example.test.cpp
#include <catch2/catch_test_macros.hpp>

#include "my_module/my_class.hpp"

TEST_CASE("MyClass::method returns expected value", "[my_class][method]") {
    MyClass obj;
    
    SECTION("with valid input") {
        REQUIRE(obj.method("valid") == expected_result);
    }
    
    SECTION("with empty input") {
        REQUIRE_THROWS_AS(obj.method(""), std::invalid_argument);
    }
}

TEST_CASE("Edge cases", "[my_class]") {
    MyClass obj;
    CHECK(obj.empty() == true);  // Continues on failure
}
```

### Running Single Tests

```bash
# By test name (partial match)
./build/tests/test_runner "MyClass::method"

# By tag
./build/tests/test_runner "[my_class]"

# Specific section
./build/tests/test_runner "Edge cases" -c "with valid input"
```

---

## CMake Conventions

### Target Naming

- Executables: `project_name` or `project_name_cli`
- Libraries: `project_name_lib` or just `project_name`
- Tests: `test_<module>` or `tests`

### Example CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.16)
project(my_project VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Main library
add_library(my_project_lib
    src/core/engine.cpp
    src/utils/helpers.cpp
)
target_include_directories(my_project_lib PUBLIC include)

# Executable
add_executable(my_project src/main.cpp)
target_link_libraries(my_project PRIVATE my_project_lib)

# Tests
option(BUILD_TESTS "Build tests" ON)
if(BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
```

---

## Code Review Checklist

Before submitting changes:
- [ ] Code compiles without warnings (`-Wall -Wextra -Werror`)
- [ ] All tests pass
- [ ] No memory leaks (run with ASan/UBSan if available)
- [ ] Follows naming conventions
- [ ] Headers have `#pragma once`
- [ ] Includes are sorted and minimal
- [ ] No `using namespace` in headers
- [ ] Smart pointers used instead of raw `new`/`delete`
- [ ] Functions marked `[[nodiscard]]` when appropriate

---

## icraw Project Specifics

### Project Overview

icraw is a **mobile AI Agent** library designed for cross-platform use. It provides:

- **Agent**: Main agent loop with tool-calling capabilities
- **Memory**: SQLite-based memory management for conversation history and daily memory
- **Prompt**: System prompt construction from workspace files
- **Skill**: SKILL.md format parsing (OpenClaw compatible)

### Key Differences from OpenClaw/QuantClaw

1. **No gateway service** - Direct library usage
2. **No shell commands** - Mobile security restriction
3. **Single session only** - No multi-session support
4. **SQLite memory storage** - Persistent conversation history with search capabilities
5. **C++ standard library file I/O** - No external file system dependencies

### Project Structure

```
.
├── CMakeLists.txt           # Main CMake configuration
├── include/icraw/           # Public headers
│   ├── core/               # Core components
│   │   ├── agent_loop.hpp
│   │   ├── memory_manager.hpp
│   │   ├── prompt_builder.hpp
│   │   ├── skill_loader.hpp
│   │   ├── content_block.hpp
│   │   └── llm_provider.hpp
│   ├── tools/              # Tool implementations
│   │   └── tool_registry.hpp
│   ├── config.hpp          # Configuration structures
│   ├── types.hpp           # Core type definitions
│   └── mobile_agent.hpp    # Main facade class
├── src/                    # Source files
│   ├── core/
│   ├── tools/
│   ├── config.cpp
│   └── mobile_agent.cpp
├── tests/                  # Catch2 tests
├── workspace/              # Default workspace
│   ├── SOUL.md            # Agent identity
│   ├── USER.md            # User information
│   └── skills/            # Skill definitions
└── AGENTS.md              # This file
```

### Dependencies

Fetched automatically by CMake:
- **nlohmann/json** (v3.11.3) - JSON library
- **Catch2** (v3.5.2) - Testing framework
- **SQLite3** (amalgamation v3.45.0) - Embedded database for memory storage
- **libcurl** (via vcpkg) - HTTP client for LLM API calls

### Build Commands

```bash
# Configure and build
cmake -B build -S .
cmake --build build

# Run tests
ctest --test-dir build --output-on-failure

# Run specific test
./build/tests/icraw_tests "[content_block]"
./build/tests/icraw_tests "[memory_manager]"
./build/tests/icraw_tests "[tool_registry]"
./build/tests/icraw_tests "[skill_loader]"
```

### Usage Example

```cpp
#include <icraw/mobile_agent.hpp>

int main() {
    // Create agent with default configuration
    auto agent = icraw::MobileAgent::create("/path/to/workspace");
    
    // Simple chat
    std::string response = agent->chat("Hello!");
    std::cout << response << std::endl;
    
    // Streaming chat
    agent->chat_stream("Tell me a story", [](const icraw::AgentEvent& event) {
        if (event.type == "text_delta") {
            std::cout << event.data["delta"].get<std::string>();
        }
    });
    
    return 0;
}
```

### Tool Implementation

icraw provides file I/O tools for mobile security:

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_file` | Read file contents | `path` (required) |
| `write_file` | Write content to file | `path` (required), `content` (required) |
| `list_files` | List files and directories | `path`, `recursive` (default: false), `pattern` |
| `grep_files` | Search text patterns in files | `pattern` (required), `path`, `use_regex`, `case_sensitive`, etc. |
| `save_memory` | Save memory consolidation | `history_entry`, `memory_update` |
| `search_memory` | Search conversation history | `query` (required), `limit` (default: 10) |

All file operations are sandboxed to the workspace directory.

#### list_files Tool

```cpp
// Parameters
{
    "path": "/path/to/dir",      // Optional, defaults to workspace
    "recursive": false,          // Optional, default false
    "pattern": "*.cpp"           // Optional, wildcard filter
}

// Response
{
    "success": true,
    "path": "/path/to/dir",
    "recursive": false,
    "entries": [
        {
            "name": "file.txt",
            "path": "/path/to/dir/file.txt",
            "type": "file",        // or "directory"
            "size": 1234,          // files only
            "modified": "2024-01-15 10:30:00"
        }
    ],
    "file_count": 1,
    "directory_count": 1,
    "total_count": 2
}
```

#### grep_files Tool

Search for text patterns in files using C++17's `std::boyer_moore_searcher` for literal strings and `std::regex` for regular expressions.

```cpp
// Parameters
{
    "pattern": "search_term",           // Required: search pattern
    "path": "/path/to/search",          // Optional: directory or file (default: workspace)
    "recursive": true,                  // Optional: search recursively (default: true)
    "use_regex": false,                 // Optional: treat pattern as regex (default: false)
    "case_sensitive": true,             // Optional: case-sensitive search (default: true)
    "max_results": 100,                 // Optional: max matches to return (default: 100)
    "include_patterns": ["*.cpp", "*.hpp"],  // Optional: file patterns to include
    "exclude_patterns": [".git", "node_modules"]  // Optional: dirs/files to exclude
}

// Response
{
    "success": true,
    "pattern": "search_term",
    "path": "/path/to/search",
    "use_regex": false,
    "case_sensitive": true,
    "matches": [
        {
            "file": "/path/to/search/file.cpp",
            "line_number": 42,
            "column": 10,
            "line": "This line contains search_term"
        }
    ],
    "total_matches": 5,
    "files_searched": 10,
    "files_with_matches": 3,
    "truncated": false   // true if max_results was reached
}
```

**Examples:**

```cpp
// Search for "TODO" in all .cpp files
{
    "pattern": "TODO",
    "include_patterns": ["*.cpp", "*.hpp"],
    "exclude_patterns": [".git", "build"]
}

// Case-insensitive regex search for function definitions
{
    "pattern": "void\\s+\\w+\\s*\\(",
    "use_regex": true,
    "case_sensitive": false
}

// Search single file
{
    "pattern": "error",
    "path": "/path/to/specific/file.log"
}
```

### Workspace Files

| File | Purpose |
|------|---------|
| `SOUL.md` | Agent identity and core capabilities |
| `USER.md` | User profile and preferences |
| `AGENTS.md` | Behavior guidelines (optional) |
| `TOOLS.md` | Tool usage guide (optional) |
| `skills/*/SKILL.md` | Skill definitions |

### SQLite Memory Storage

icraw uses SQLite for persistent memory storage. The database file `memory.db` is created in the workspace directory.

#### Database Schema

```sql
-- Conversation messages
CREATE TABLE messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    role TEXT NOT NULL,           -- "user", "assistant", "system", "tool"
    content TEXT NOT NULL,
    timestamp TEXT NOT NULL,      -- ISO 8601
    session_id TEXT NOT NULL DEFAULT 'default',
    metadata TEXT                  -- JSON metadata
);

-- Conversation summaries
CREATE TABLE summaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    summary TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    message_count INTEGER DEFAULT 0
);

-- Daily memory entries
CREATE TABLE daily_memory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL,           -- YYYY-MM-DD
    content TEXT NOT NULL,
    created_at TEXT NOT NULL
);
```

#### MemoryManager API

```cpp
// Add a message to conversation history
int64_t add_message(const std::string& role, 
                    const std::string& content,
                    const std::string& session_id = "default",
                    const nlohmann::json& metadata = {});

// Get recent messages (chronological order)
std::vector<MemoryEntry> get_recent_messages(int limit = 50,
                                              const std::string& session_id = "default") const;

// Search memory for content
std::vector<MemoryEntry> search_memory(const std::string& query, int limit = 10) const;

// Get message count
int64_t get_message_count(const std::string& session_id = "default") const;

// Clear history
void clear_history(const std::string& session_id = "default");

// Daily memory
void save_daily_memory(const std::string& content);
std::vector<MemoryEntry> get_daily_memory(const std::string& date = "") const;

// Summaries
int64_t create_summary(const std::string& session_id, const std::string& summary, int message_count);
std::optional<ConversationSummary> get_latest_summary(const std::string& session_id = "default") const;
```

### SKILL.md Format

```markdown
---
description: Skill description
emoji: 🔧
requiredEnvs:
  - API_KEY
---

# Skill Title

Skill instructions and examples...
```
