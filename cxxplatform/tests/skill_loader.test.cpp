#include <catch2/catch_test_macros.hpp>
#include "icraw/core/skill_loader.hpp"
#include "icraw/config.hpp"
#include <filesystem>
#include <fstream>

namespace fs = std::filesystem;

TEST_CASE("SkillLoader loads skills from directory", "[skill_loader]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_skills";
    auto skill_dir = temp_dir / "test_skill";
    fs::create_directories(skill_dir);
    
    // Create SKILL.md with frontmatter
    std::ofstream(skill_dir / "SKILL.md") << R"(---
description: A test skill for unit testing
emoji: 🧪
---
# Test Skill

This is the skill content.
It provides instructions for testing.
)";
    
    icraw::SkillLoader loader;
    auto skills = loader.load_skills_from_directory(temp_dir);
    
    REQUIRE(skills.size() == 1);
    REQUIRE(skills[0].name == "test_skill");
    REQUIRE(skills[0].description == "A test skill for unit testing");
    REQUIRE(skills[0].emoji == "🧪");
    REQUIRE(skills[0].content.find("Test Skill") != std::string::npos);
    
    // Cleanup
    fs::remove_all(temp_dir);
}

TEST_CASE("SkillLoader handles skills without frontmatter", "[skill_loader]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_skills_simple";
    auto skill_dir = temp_dir / "simple_skill";
    fs::create_directories(skill_dir);
    
    // Create SKILL.md without frontmatter
    std::ofstream(skill_dir / "SKILL.md") << R"(# Simple Skill

Just plain markdown content.
)";
    
    icraw::SkillLoader loader;
    auto skills = loader.load_skills_from_directory(temp_dir);
    
    REQUIRE(skills.size() == 1);
    REQUIRE(skills[0].content.find("Simple Skill") != std::string::npos);
    
    // Cleanup
    fs::remove_all(temp_dir);
}

TEST_CASE("SkillLoader::get_skill_context formats skills correctly", "[skill_loader]") {
    std::vector<icraw::SkillMetadata> skills;
    
    icraw::SkillMetadata skill1;
    skill1.name = "skill_one";
    skill1.description = "First skill";
    skill1.content = "Content for skill one";
    skills.push_back(skill1);
    
    icraw::SkillMetadata skill2;
    skill2.name = "skill_two";
    skill2.description = "Second skill";
    skill2.content = "Content for skill two";
    skills.push_back(skill2);
    
    icraw::SkillLoader loader;
    std::string context = loader.get_skill_context(skills);
    
    REQUIRE(context.find("## Skill: skill_one") != std::string::npos);
    REQUIRE(context.find("## Skill: skill_two") != std::string::npos);
    REQUIRE(context.find("First skill") != std::string::npos);
    REQUIRE(context.find("Content for skill one") != std::string::npos);
}

TEST_CASE("SkillLoader returns empty for nonexistent directory", "[skill_loader]") {
    icraw::SkillLoader loader;
    auto skills = loader.load_skills_from_directory("/nonexistent/path");
    
    REQUIRE(skills.empty());
}

TEST_CASE("SkillLoader deduplicates skills from multiple directories", "[skill_loader]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_skills_multi";
    auto dir1 = temp_dir / "dir1" / "shared_skill";
    auto dir2 = temp_dir / "dir2" / "shared_skill";
    fs::create_directories(dir1);
    fs::create_directories(dir2);
    
    // Create same-named skill in both directories
    std::ofstream(dir1 / "SKILL.md") << "---\ndescription: First version\n---\nContent 1";
    std::ofstream(dir2 / "SKILL.md") << "---\ndescription: Second version\n---\nContent 2";
    
    icraw::SkillsConfig config;
    config.extra_dirs.push_back((temp_dir / "dir2").string());
    
    icraw::SkillLoader loader;
    auto skills = loader.load_skills(config, temp_dir / "dir1");
    
    // Should only have one skill with this name
    REQUIRE(skills.size() == 1);
    
    // Cleanup
    fs::remove_all(temp_dir);
}
