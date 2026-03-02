#include <catch2/catch_test_macros.hpp>
#include "icraw/core/content_block.hpp"
#include "icraw/types.hpp"

TEST_CASE("ContentBlock::make_text creates valid text block", "[content_block]") {
    auto block = icraw::ContentBlock::make_text("Hello, World!");
    
    REQUIRE(block.type == "text");
    REQUIRE(block.text == "Hello, World!");
}

TEST_CASE("ContentBlock::make_tool_use creates valid tool_use block", "[content_block]") {
    nlohmann::json input = {{"path", "/tmp/test.txt"}};
    auto block = icraw::ContentBlock::make_tool_use("tool_123", "read_file", input);
    
    REQUIRE(block.type == "tool_use");
    REQUIRE(block.id == "tool_123");
    REQUIRE(block.name == "read_file");
    REQUIRE(block.input["path"] == "/tmp/test.txt");
}

TEST_CASE("ContentBlock::make_tool_result creates valid tool_result block", "[content_block]") {
    auto block = icraw::ContentBlock::make_tool_result("tool_123", "File content here");
    
    REQUIRE(block.type == "tool_result");
    REQUIRE(block.tool_use_id == "tool_123");
    REQUIRE(block.content == "File content here");
}

TEST_CASE("ContentBlock::to_json and from_json are symmetric", "[content_block]") {
    SECTION("text block") {
        auto original = icraw::ContentBlock::make_text("Test message");
        auto json = original.to_json();
        auto restored = icraw::ContentBlock::from_json(json);
        
        REQUIRE(restored.type == original.type);
        REQUIRE(restored.text == original.text);
    }
    
    SECTION("tool_use block") {
        nlohmann::json input = {{"key", "value"}};
        auto original = icraw::ContentBlock::make_tool_use("id1", "test_tool", input);
        auto json = original.to_json();
        auto restored = icraw::ContentBlock::from_json(json);
        
        REQUIRE(restored.type == original.type);
        REQUIRE(restored.id == original.id);
        REQUIRE(restored.name == original.name);
        REQUIRE(restored.input["key"] == "value");
    }
    
    SECTION("tool_result block") {
        auto original = icraw::ContentBlock::make_tool_result("id1", "result content");
        auto json = original.to_json();
        auto restored = icraw::ContentBlock::from_json(json);
        
        REQUIRE(restored.type == original.type);
        REQUIRE(restored.tool_use_id == original.tool_use_id);
        REQUIRE(restored.content == original.content);
    }
}

TEST_CASE("Message constructors work correctly", "[message]") {
    SECTION("Default constructor") {
        icraw::Message msg;
        REQUIRE(msg.role.empty());
        REQUIRE(msg.content.empty());
    }
    
    SECTION("Role and text constructor") {
        icraw::Message msg("user", "Hello!");
        REQUIRE(msg.role == "user");
        REQUIRE(msg.content.size() == 1);
        REQUIRE(msg.text() == "Hello!");
    }
}

TEST_CASE("Message::text() extracts text correctly", "[message]") {
    icraw::Message msg("assistant", "");
    msg.content.push_back(icraw::ContentBlock::make_text("Part 1"));
    msg.content.push_back(icraw::ContentBlock::make_text("Part 2"));
    
    REQUIRE(msg.text() == "Part 1Part 2");
}

TEST_CASE("Message::to_json and from_json are symmetric", "[message]") {
    icraw::Message original("assistant", "Test response");
    auto json = original.to_json();
    auto restored = icraw::Message::from_json(json);
    
    REQUIRE(restored.role == original.role);
    REQUIRE(restored.text() == original.text());
}
