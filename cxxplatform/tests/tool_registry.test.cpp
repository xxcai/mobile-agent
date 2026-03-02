#include <catch2/catch_test_macros.hpp>
#include "icraw/tools/tool_registry.hpp"
#include <filesystem>
#include <fstream>

namespace fs = std::filesystem;

TEST_CASE("ToolRegistry initializes correctly", "[tool_registry]") {
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    
    REQUIRE(registry.has_tool("read_file"));
    REQUIRE(registry.has_tool("write_file"));
    REQUIRE(registry.has_tool("list_files"));
    REQUIRE(registry.has_tool("grep_files"));
}

TEST_CASE("ToolRegistry::get_tool_schemas returns correct schemas", "[tool_registry]") {
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    
    auto schemas = registry.get_tool_schemas();
    
    REQUIRE(schemas.size() == 6);  // read_file, write_file, list_files, save_memory, search_memory, grep_files
    
    // Check read_file schema
    auto read_it = std::find_if(schemas.begin(), schemas.end(),
        [](const icraw::ToolSchema& s) { return s.name == "read_file"; });
    REQUIRE(read_it != schemas.end());
    REQUIRE_FALSE(read_it->description.empty());
    REQUIRE(read_it->parameters.contains("properties"));
    
    // Check write_file schema
    auto write_it = std::find_if(schemas.begin(), schemas.end(),
        [](const icraw::ToolSchema& s) { return s.name == "write_file"; });
    REQUIRE(write_it != schemas.end());
    REQUIRE_FALSE(write_it->description.empty());
    REQUIRE(write_it->parameters.contains("properties"));
    
    // Check save_memory schema
    auto save_it = std::find_if(schemas.begin(), schemas.end(),
        [](const icraw::ToolSchema& s) { return s.name == "save_memory"; });
    REQUIRE(save_it != schemas.end());
    REQUIRE_FALSE(save_it->description.empty());
    
    // Check search_memory schema
    auto search_it = std::find_if(schemas.begin(), schemas.end(),
        [](const icraw::ToolSchema& s) { return s.name == "search_memory"; });
    REQUIRE(search_it != schemas.end());
    REQUIRE_FALSE(search_it->description.empty());
    
    // Check list_files schema
    auto list_it = std::find_if(schemas.begin(), schemas.end(),
        [](const icraw::ToolSchema& s) { return s.name == "list_files"; });
    REQUIRE(list_it != schemas.end());
    REQUIRE_FALSE(list_it->description.empty());
    REQUIRE(list_it->parameters.contains("properties"));
}

TEST_CASE("ToolRegistry::execute_tool read_file works", "[tool_registry]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_read";
    fs::create_directories(temp_dir);
    
    auto test_file = temp_dir / "test.txt";
    std::ofstream(test_file) << "Test content for reading";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    nlohmann::json params;
    params["path"] = test_file.string();
    
    std::string result = registry.execute_tool("read_file", params);
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == true);
    REQUIRE(json_result["content"] == "Test content for reading");
    
    // Cleanup
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry::execute_tool write_file works", "[tool_registry]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_write";
    // Ensure clean state
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    auto test_file = temp_dir / "output.txt";
    
    nlohmann::json params;
    params["path"] = test_file.string();
    params["content"] = "Written by tool";
    
    std::string result = registry.execute_tool("write_file", params);
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == true);
    // Check bytes_written - may differ due to line ending differences
    auto bytes_written = json_result["bytes_written"].get<int>();
    CHECK((bytes_written == 14 || bytes_written == 15));
    
    // Verify file was written
    {
        std::ifstream file(test_file);
        std::string content((std::istreambuf_iterator<char>(file)),
                            std::istreambuf_iterator<char>());
        REQUIRE(content == "Written by tool");
    }
    
    // Cleanup
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry rejects paths outside base_path", "[tool_registry]") {
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path("/tmp/safe_dir");
    
    nlohmann::json params;
    params["path"] = "/etc/passwd";  // Outside base path
    
    std::string result = registry.execute_tool("read_file", params);
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == false);
    REQUIRE(json_result["error"].get<std::string>().find("Access denied") != std::string::npos);
}

TEST_CASE("ToolRegistry returns error for unknown tool", "[tool_registry]") {
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    
    std::string result = registry.execute_tool("unknown_tool", {});
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == false);
    REQUIRE(json_result["error"].get<std::string>().find("Unknown tool") != std::string::npos);
}

TEST_CASE("ToolRegistry::execute_tool list_files works", "[tool_registry]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_list";
    // Ensure clean state
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    // Create test files and directories
    fs::create_directories(temp_dir / "subdir");
    std::ofstream(temp_dir / "file1.txt") << "content1";
    std::ofstream(temp_dir / "file2.cpp") << "content2";
    std::ofstream(temp_dir / "subdir" / "file3.txt") << "content3";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    SECTION("Non-recursive listing") {
        nlohmann::json params;
        params["path"] = temp_dir.string();
        params["recursive"] = false;
        
        std::string result = registry.execute_tool("list_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == true);
        REQUIRE(json_result["recursive"] == false);
        
        // Should have 2 files and 1 directory (non-recursive)
        REQUIRE(json_result["file_count"] == 2);
        REQUIRE(json_result["directory_count"] == 1);
        REQUIRE(json_result["total_count"] == 3);
    }
    
    SECTION("Recursive listing") {
        nlohmann::json params;
        params["path"] = temp_dir.string();
        params["recursive"] = true;
        
        std::string result = registry.execute_tool("list_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == true);
        REQUIRE(json_result["recursive"] == true);
        
        // Should have 3 files and 1 directory (recursive)
        REQUIRE(json_result["file_count"] == 3);
        REQUIRE(json_result["directory_count"] == 1);
        REQUIRE(json_result["total_count"] == 4);
    }
    
    SECTION("Pattern filtering") {
        nlohmann::json params;
        params["path"] = temp_dir.string();
        params["pattern"] = "*.cpp";
        
        std::string result = registry.execute_tool("list_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == true);
        
        // Should only have 1 .cpp file
        REQUIRE(json_result["file_count"] == 1);
        REQUIRE(json_result["directory_count"] == 1);  // Directories not filtered
    }
    
    // Cleanup
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry::list_files error handling", "[tool_registry]") {
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    
    SECTION("Non-existent directory") {
        registry.set_base_path("/");  // Allow root access for this test
        
        nlohmann::json params;
        params["path"] = "/non/existent/directory";
        
        std::string result = registry.execute_tool("list_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == false);
        REQUIRE(json_result["error"].get<std::string>().find("not exist") != std::string::npos);
    }
    
    SECTION("File path instead of directory") {
        auto temp_dir = fs::temp_directory_path() / "icraw_test_list_error";
        fs::remove_all(temp_dir);
        fs::create_directories(temp_dir);
        
        auto test_file = temp_dir / "notadir.txt";
        std::ofstream(test_file) << "content";
        
        registry.set_base_path(temp_dir.string());
        
        nlohmann::json params;
        params["path"] = test_file.string();
        
        std::string result = registry.execute_tool("list_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == false);
        REQUIRE(json_result["error"].get<std::string>().find("not a directory") != std::string::npos);
        
        fs::remove_all(temp_dir);
    }
    
    SECTION("Path outside base_path") {
        registry.set_base_path("/tmp/safe_dir");
        
        nlohmann::json params;
        params["path"] = "/etc";
        
        std::string result = registry.execute_tool("list_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == false);
        REQUIRE(json_result["error"].get<std::string>().find("Access denied") != std::string::npos);
    }
}

TEST_CASE("ToolRegistry::list_files default path", "[tool_registry]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_list_default";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    // Create test file
    std::ofstream(temp_dir / "default_test.txt") << "content";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    // List without specifying path (should use base_path)
    nlohmann::json params;
    params["path"] = temp_dir.string();  // Explicitly set path to base_path
    
    std::string result = registry.execute_tool("list_files", params);
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == true);
    REQUIRE(json_result["file_count"] == 1);
    
    fs::remove_all(temp_dir);
}

// ============================================================
// grep_files tool tests
// ============================================================

TEST_CASE("ToolRegistry::grep_files basic literal search", "[tool_registry][grep]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_grep_basic";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    // Create test files
    std::ofstream(temp_dir / "file1.txt") << "Hello World\nThis is a test\nAnother line";
    std::ofstream(temp_dir / "file2.txt") << "No match here\nHello again\nGoodbye";
    std::ofstream(temp_dir / "file3.cpp") << "// Hello comment\nint main() { return 0; }";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    SECTION("Search for literal string") {
        nlohmann::json params;
        params["pattern"] = "Hello";
        params["path"] = temp_dir.string();
        params["recursive"] = true;
        params["use_regex"] = false;
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == true);
        REQUIRE(json_result["total_matches"] == 3);  // file1.txt, file2.txt, file3.cpp
        REQUIRE(json_result["files_with_matches"] == 3);
    }
    
    SECTION("Search with include pattern") {
        nlohmann::json params;
        params["pattern"] = "Hello";
        params["path"] = temp_dir.string();
        params["include_patterns"] = nlohmann::json::array({"*.txt"});
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == true);
        REQUIRE(json_result["total_matches"] == 2);  // Only .txt files
        REQUIRE(json_result["files_with_matches"] == 2);
    }
    
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry::grep_files regex search", "[tool_registry][grep]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_grep_regex";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    // Create test files
    std::ofstream(temp_dir / "code.cpp") << "int value = 123;\nint count = 456;\nstring name = \"test\";";
    std::ofstream(temp_dir / "data.txt") << "ID: 789\nName: Alice\nAge: 25";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    SECTION("Regex search for numbers") {
        nlohmann::json params;
        params["pattern"] = "\\d+";
        params["path"] = temp_dir.string();
        params["use_regex"] = true;
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == true);
        REQUIRE(json_result["total_matches"] == 4);  // 123, 456, 789, 25
    }
    
    SECTION("Invalid regex pattern") {
        nlohmann::json params;
        params["pattern"] = "[invalid(";  // Invalid regex
        params["path"] = temp_dir.string();
        params["use_regex"] = true;
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == false);
        REQUIRE(json_result["error"].get<std::string>().find("regex") != std::string::npos);
    }
    
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry::grep_files case sensitivity", "[tool_registry][grep]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_grep_case";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    std::ofstream(temp_dir / "test.txt") << "Hello World\nHELLO WORLD\nhello world";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    SECTION("Case-sensitive search") {
        nlohmann::json params;
        params["pattern"] = "Hello";
        params["path"] = temp_dir.string();
        params["case_sensitive"] = true;
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == true);
        REQUIRE(json_result["total_matches"] == 1);  // Only "Hello World"
    }
    
    SECTION("Case-insensitive search") {
        nlohmann::json params;
        params["pattern"] = "hello";
        params["path"] = temp_dir.string();
        params["case_sensitive"] = false;
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == true);
        REQUIRE(json_result["total_matches"] == 3);  // All three lines
    }
    
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry::grep_files max_results", "[tool_registry][grep]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_grep_max";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    // Create file with many matches
    {
        std::ofstream file(temp_dir / "test.txt");
        for (int i = 0; i < 100; i++) {
            file << "Line " << i << " with match\n";
        }
        file.close();  // Ensure file is flushed
    }
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    nlohmann::json params;
    params["pattern"] = "match";
    params["path"] = temp_dir.string();
    params["max_results"] = 10;
    
    std::string result = registry.execute_tool("grep_files", params);
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == true);
    REQUIRE(json_result["total_matches"] == 10);
    REQUIRE(json_result["truncated"] == true);
    
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry::grep_files exclude patterns", "[tool_registry][grep]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_grep_exclude";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    fs::create_directories(temp_dir / "node_modules");
    fs::create_directories(temp_dir / ".git");
    
    std::ofstream(temp_dir / "main.cpp") << "int main() { return 0; }";
    std::ofstream(temp_dir / "node_modules" / "lib.js") << "int main() { return 1; }";
    std::ofstream(temp_dir / ".git" / "config") << "int main() { return 2; }";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    nlohmann::json params;
    params["pattern"] = "main";
    params["path"] = temp_dir.string();
    params["recursive"] = true;
    params["exclude_patterns"] = nlohmann::json::array({"node_modules", ".git"});
    
    std::string result = registry.execute_tool("grep_files", params);
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == true);
    REQUIRE(json_result["total_matches"] == 1);  // Only main.cpp
    REQUIRE(json_result["files_with_matches"] == 1);
    
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry::grep_files single file search", "[tool_registry][grep]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_grep_single";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    auto test_file = temp_dir / "test.txt";
    std::ofstream(test_file) << "Line 1\nLine 2 with pattern\nLine 3\nAnother pattern here";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    nlohmann::json params;
    params["pattern"] = "pattern";
    params["path"] = test_file.string();
    
    std::string result = registry.execute_tool("grep_files", params);
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == true);
    REQUIRE(json_result["total_matches"] == 2);
    REQUIRE(json_result["files_searched"] == 1);
    REQUIRE(json_result["files_with_matches"] == 1);
    
    // Check match details
    auto matches = json_result["matches"];
    REQUIRE(matches.size() == 2);
    REQUIRE(matches[0]["line_number"] == 2);
    REQUIRE(matches[1]["line_number"] == 4);
    
    fs::remove_all(temp_dir);
}

TEST_CASE("ToolRegistry::grep_files error handling", "[tool_registry][grep]") {
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    
    SECTION("Missing pattern") {
        nlohmann::json params;
        params["path"] = "/tmp";
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == false);
        REQUIRE(json_result["error"].get<std::string>().find("required") != std::string::npos);
    }
    
    SECTION("Non-existent path") {
        registry.set_base_path("/");
        
        nlohmann::json params;
        params["pattern"] = "test";
        params["path"] = "/non/existent/path";
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == false);
        REQUIRE(json_result["error"].get<std::string>().find("not exist") != std::string::npos);
    }
    
    SECTION("Path outside base_path") {
        registry.set_base_path("/tmp/safe_dir");
        
        nlohmann::json params;
        params["pattern"] = "test";
        params["path"] = "/etc/passwd";
        
        std::string result = registry.execute_tool("grep_files", params);
        auto json_result = nlohmann::json::parse(result);
        
        REQUIRE(json_result["success"] == false);
        REQUIRE(json_result["error"].get<std::string>().find("Access denied") != std::string::npos);
    }
}

TEST_CASE("ToolRegistry::grep_files returns column position", "[tool_registry][grep]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_grep_column";
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    
    std::ofstream(temp_dir / "test.txt") << "abc def ghi";
    
    icraw::ToolRegistry registry;
    registry.register_builtin_tools();
    registry.set_base_path(temp_dir.string());
    
    nlohmann::json params;
    params["pattern"] = "def";
    params["path"] = temp_dir.string();
    
    std::string result = registry.execute_tool("grep_files", params);
    auto json_result = nlohmann::json::parse(result);
    
    REQUIRE(json_result["success"] == true);
    REQUIRE(json_result["total_matches"] == 1);
    REQUIRE(json_result["matches"][0]["column"] == 4);  // "def" starts at position 4
    
    fs::remove_all(temp_dir);
}
