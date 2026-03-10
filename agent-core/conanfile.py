from conan import ConanFile
from conan.tools.cmake import cmake_layout


class AgentConan(ConanFile):
    settings = "os", "compiler", "build_type", "arch"
    generators = ["CMakeDeps", "CMakeToolchain"]
    options = {"shared": [True, False]}
    default_options = {"shared": True}
    requires = [
        "libcurl/8.1.2",
        "nlohmann_json/3.11.3",
        "spdlog/1.15.1",
        "zlib/1.3.1",
        "sqlite3/3.49.1",
    ]

    def configure(self):
        self.options["spdlog"].header_only = True
        self.options["spdlog"].fmt = "bundled"
        self.options["libcurl"].shared = True
        self.options["zlib"].shared = True
        self.options["sqlite3"].shared = True

    def layout(self):
        cmake_layout(self)
        self.folders.build = f"build/{self.settings.arch}/{self.settings.build_type}"
        self.folders.generators = f"build/{self.settings.arch}/{self.settings.build_type}/generators"
