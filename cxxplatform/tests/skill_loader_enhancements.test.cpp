#include <catch2/catch_test_macros.hpp>
#include "icraw/core/skill_loader.hpp"
#include "icraw/config.hpp"
#include <filesystem>
#include <fstream>

namespace fs = std::filesystem;

TEST_CASE("SkillLoader::build_skills_summary formats correctly", "[skill_loader]") {
    std::vector<icraw::SkillMetadata> skills;

    // Skill with OS restriction (should be available on this platform)
    icraw::SkillMetadata skill1;
    skill1.name = "test_skill";
    skill1.description = "A test skill";
    skill1.content = "This is the skill content.";
    skills.push_back(skill1);

    icraw::SkillLoader loader;
    std::string summary = loader.build_skills_summary(skills);

    REQUIRE(summary.find("<skills>") != std::string::npos);
    REQUIRE(summary.find("<name>test_skill</name>") != std::string::npos);
    REQUIRE(summary.find("<description>A test skill</description>") != std::string::npos);
    REQUIRE(summary.find("available=\"true\"") != std::string::npos);
}

TEST_CASE("SkillLoader::get_always_skills filters correctly", "[skill_loader]") {
    std::vector<icraw::SkillMetadata> skills;

    icraw::SkillMetadata always_skill;
    always_skill.name = "always_one";
    always_skill.always = true;
    skills.push_back(always_skill);

    icraw::SkillMetadata conditional_skill;
    conditional_skill.name = "conditional_one";
    conditional_skill.always = false;
    skills.push_back(conditional_skill);

    icraw::SkillLoader loader;
    auto always_skills = loader.get_always_skills(skills);

    REQUIRE(always_skills.size() == 1);
    REQUIRE(always_skills[0].name == "always_one");
}

TEST_CASE("SkillLoader::validate_name follows AgentSkills spec", "[skill_loader]") {
    icraw::SkillLoader loader;

    // Valid names
    REQUIRE(loader.validate_name("test") == true);
    REQUIRE(loader.validate_name("test-skill") == true);
    REQUIRE(loader.validate_name("my-skill-123") == true);
    REQUIRE(loader.validate_name("a") == true);

    // Invalid names (too long)
    REQUIRE(loader.validate_name(std::string(65, 'a')) == false);

    // Invalid names (uppercase)
    REQUIRE(loader.validate_name("Test") == false);
    REQUIRE(loader.validate_name("TestSkill") == false);

    // Invalid names (leading hyphen)
    REQUIRE(loader.validate_name("-test") == false);

    // Invalid names (trailing hyphen)
    REQUIRE(loader.validate_name("test-") == false);

    // Invalid names (consecutive hyphens)
    REQUIRE(loader.validate_name("test--skill") == false);

    // Invalid names (special characters)
    REQUIRE(loader.validate_name("test_skill") == false);  // underscore
    REQUIRE(loader.validate_name("test.skill") == false);  // dot
    REQUIRE(loader.validate_name("test skill") == false);  // space
}

TEST_CASE("SkillLoader::normalize_name converts to valid format", "[skill_loader]") {
    icraw::SkillLoader loader;

    REQUIRE(loader.normalize_name("Test Skill") == "test-skill");
    REQUIRE(loader.normalize_name("MySkill-123") == "myskill-123");
    REQUIRE(loader.normalize_name("  Test  Skill  ") == "test-skill");
    REQUIRE(loader.normalize_name("Test__Skill") == "testskill");  // underscores are removed (not converted to hyphens)
    REQUIRE(loader.normalize_name("-test-skill-") == "test-skill");  // leading/trailing hyphens removed
    REQUIRE(loader.normalize_name("test--skill") == "test-skill");  // consecutive hyphens collapsed
}

TEST_CASE("SkillLoader::check_env_var works on all platforms", "[skill_loader]") {
    icraw::SkillLoader loader;

    // This test should pass regardless of whether the env var exists
    std::string result = loader.check_env_var("NONEXISTENT_ENV_VAR_12345");

    // Just ensure the function can be called without throwing
    // Result will be empty if env var doesn't exist
    (void)result;  // Suppress unused variable warning
}

TEST_CASE("SkillLoader handles nested YAML metadata", "[skill_loader]") {
    auto temp_dir = fs::temp_directory_path() / "icraw_test_nested_yaml";
    auto skill_dir = temp_dir / "nested_skill";
    fs::create_directories(skill_dir);

    // Create SKILL.md with correct format (all fields at top level per SKILL.md spec)
    std::ofstream(skill_dir / "SKILL.md") << R"(---
description: A skill with environment requirements
emoji: "🔧"
requiredEnvs:
  - API_KEY
  - SECRET_KEY
requiredBins:
  - python3
os:
  - linux
  - darwin
always: true
---

# Environment Requirements Test

This skill requires specific environment variables and binaries.
)";

    icraw::SkillLoader loader;
    auto skills = loader.load_skills_from_directory(temp_dir);

    REQUIRE(skills.size() == 1);
    REQUIRE(skills[0].name == "nested_skill");
    REQUIRE(skills[0].description == "A skill with environment requirements");
    REQUIRE(skills[0].emoji == "🔧");
    REQUIRE(skills[0].required_envs.size() == 2);
    REQUIRE(skills[0].required_envs[0] == "API_KEY");
    REQUIRE(skills[0].required_envs[1] == "SECRET_KEY");
    REQUIRE(skills[0].required_bins.size() == 1);
    REQUIRE(skills[0].required_bins[0] == "python3");
    REQUIRE(skills[0].os_restrict.size() == 2);
    REQUIRE(skills[0].os_restrict[0] == "linux");
    REQUIRE(skills[0].os_restrict[1] == "darwin");
    REQUIRE(skills[0].always == true);
    REQUIRE(skills[0].content.find("Environment Requirements Test") != std::string::npos);

    // Cleanup
    fs::remove_all(temp_dir);
}
