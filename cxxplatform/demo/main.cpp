#ifdef _WIN32
#include <windows.h>
#endif

#include <iostream>
#include <string>
#include <atomic>
#include <csignal>
#include <cstdlib>
#include <icraw/mobile_agent.hpp>
#include <icraw/core/llm_provider.hpp>
#include <icraw/core/logger.hpp>

std::atomic<bool> g_running(true);

void signal_handler(int) {
    g_running = false;
    std::cout << "\n\nStopping..." << std::endl;
}

void print_usage(const char* program_name) {
    std::cout << "icraw CLI Agent Demo\n\n";
    std::cout << "Usage: " << program_name << " [options]\n";
    std::cout << "  --api-key <key>      OpenAI API key (or set OPENAI_API_KEY env)\n";
    std::cout << "  --base-url <url>     API base URL (default: https://api.openai.com/v1)\n";
    std::cout << "  --model <model>      Model name (default: gpt-4o)\n";
    std::cout << "  --workspace <path>   Workspace directory (default: ~/.icraw/workspace)\n";
    std::cout << "  --skill <path>       Skills directory (default: <workspace>/skills)\n";
    std::cout << "  --log <path>         Log directory (default: disabled)\n";
    std::cout << "  --log-level <level>  Log level: trace, debug, info, warn, error (default: info)\n";
    std::cout << "  --no-stream          Disable streaming output\n";
    std::cout << "  --help               Show this help message\n";
}

void print_welcome() {
    std::cout << "\n";
    std::cout << "==================================================\n";
    std::cout << "            icraw CLI Agent Demo\n";
    std::cout << "              Type /exit to quit\n";
    std::cout << "==================================================\n\n";
}

int main(int argc, char* argv[]) {
    // Set console to UTF-8 mode on Windows
#ifdef _WIN32
    SetConsoleCP(CP_UTF8);
    SetConsoleOutputCP(CP_UTF8);
    // _setmode(_O_U16TEXT);  // Removed - this causes issues with std::string
#endif
    std::string api_key;
    std::string base_url = "https://api.openai.com/v1";
    std::string model = "gpt-4o";
    std::string workspace_path;
    std::string skill_path;
    std::string log_path;
    std::string log_level = "info";
    bool use_stream = true;

    // Parse command line arguments
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--api-key" && i + 1 < argc) {
            api_key = argv[++i];
        } else if (arg == "--base-url" && i + 1 < argc) {
            base_url = argv[++i];
        } else if (arg == "--model" && i + 1 < argc) {
            model = argv[++i];
        } else if (arg == "--workspace" && i + 1 < argc) {
            workspace_path = argv[++i];
        } else if (arg == "--skill" && i + 1 < argc) {
            skill_path = argv[++i];
        } else if (arg == "--log" && i + 1 < argc) {
            log_path = argv[++i];
        } else if (arg == "--log-level" && i + 1 < argc) {
            log_level = argv[++i];
        } else if (arg == "--no-stream") {
            use_stream = false;
        } else if (arg == "--help") {
            print_usage(argv[0]);
            return 0;
        }
    }

    // Check for API key
    if (api_key.empty()) {
        const char* env_key = std::getenv("OPENAI_API_KEY");
        if (env_key) {
            api_key = env_key;
        }
    }

    if (api_key.empty()) {
        std::cerr << "Error: No API key provided.\n";
        std::cerr << "Set OPENAI_API_KEY environment variable or use --api-key option.\n";
        return 1;
    }

    // Setup signal handler
    std::signal(SIGINT, signal_handler);

    try {
        // Create configuration
        icraw::IcrawConfig config;
        config.provider.api_key = api_key;
        config.provider.base_url = base_url;
        config.agent.model = model;
        
        // Set workspace path
        if (workspace_path.empty()) {
            workspace_path = icraw::IcrawConfig::default_workspace_path().string();
        }
        config.workspace_path = workspace_path;
        
        // Set skills path
        if (!skill_path.empty()) {
            config.skills.path = skill_path;
        }
        
        // Initialize logger if log directory is specified
        if (!log_path.empty()) {
            std::cout << "Initializing logger with path: " << log_path << ", level: " << log_level << std::endl;
            icraw::Logger::get_instance().init(log_path, log_level);
            config.logging.enabled = true;
            config.logging.directory = log_path;
            config.logging.level = log_level;
            
            // Verify logger is initialized
            if (icraw::Logger::get_instance().is_initialized()) {
                std::cout << "Logger initialized successfully!" << std::endl;
            } else {
                std::cout << "Logger initialization failed!" << std::endl;
            }
        }
        
        // Create the agent using the config directly
        icraw::MobileAgent agent(config);
        
        print_welcome();
        std::cout << "Agent ready! Type your message and press Enter.\n";
        std::cout << "Commands: /exit, /clear, /help\n\n";
        
        // Main chat loop
        while (g_running) {
            std::cout << "You: ";
            std::cout.flush();
            
            std::string input;
            // Check if stdin is still valid before reading
            if (!std::getline(std::cin, input)) {
                // stdin closed or error
                break;
            }
            
            if (!g_running) break;
            
            if (input.empty()) {
                continue;
            }
            
            // Handle commands
            if (input == "/exit" || input == "/quit" || input == "/q") {
                std::cout << "Goodbye!\n";
                break;
            }
            
            if (input == "/clear" || input == "/reset") {
                agent.clear_history();
                std::cout << "Conversation history cleared.\n\n";
                continue;
            }
            
            if (input == "/help") {
                std::cout << "\nCommands:\n";
                std::cout << "  /exit, /quit, /q  - Exit the program\n";
                std::cout << "  /clear, /reset    - Clear conversation history\n";
                std::cout << "  /help             - Show this help message\n\n";
                continue;
            }
            
            // Send message to agent
            std::cout << "\nAssistant: ";
            std::cout.flush();
            
            if (use_stream) {
                // Streaming mode
                agent.chat_stream(input, [&](const icraw::AgentEvent& event) {
                    if (!g_running) return;
                    
                    if (event.type == "text_delta") {
                        if (event.data.contains("delta")) {
                            std::string delta = event.data["delta"].get<std::string>();
                            std::cout << delta;
                            std::cout.flush();
                        }
                    } else if (event.type == "tool_use") {
                        // Show when a tool is being called
                        std::string tool_name = event.data.value("name", "unknown");
                        std::cout << "\n[Calling tool: " << tool_name << "]\n";
                        std::cout.flush();
                    } else if (event.type == "tool_result") {
                        // Show tool result
                        std::string content = event.data.value("content", "");
                        std::cout << "\n[Tool result: " << content << "]\n";
                        std::cout.flush();
                    } else if (event.type == "message_end") {
                        std::string finish_reason = event.data.value("finish_reason", "");
                        std::cout << "\n[End: " << finish_reason << "]\n";
                        std::cout.flush();
                    }
                });
                
                std::cout << "\n\n";
            } else {
                // Non-streaming mode
                std::string response = agent.chat(input);
                std::cout << response << "\n\n";
            }
        }
        
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }
}
