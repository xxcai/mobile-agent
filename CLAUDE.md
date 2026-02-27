# CLAUDE.md - 项目规范

## 项目概述

- **项目名称**: Mobile Agent
- **包名**: com.hh.agent
- **构建工具**: Gradle 8.12.1, AGP 8.3.2
- **语言**: Java 21, C++ (NDK)
- **架构**: Android应用 + Native Library模块

## 目录结构

```
mobile-agent/
├── app/                    # Android应用模块
│   └── src/main/
│       ├── java/com/hh/agent/
│       └── res/
├── lib/                    # Native Library模块
│   └── src/main/
│       ├── cpp/            # C++源代码
│       └── java/com/hh/agent/lib/
├── build.gradle           # 根项目配置
├── settings.gradle
└── gradle.properties
```

## 提交规范

### 提交格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| 类型 | 说明 |
|------|------|
| feat | 新功能 |
| fix | Bug修复 |
| refactor | 代码重构 |
| perf | 性能优化 |
| build | 构建相关 |
| ci | CI/CD配置 |
| docs | 文档更新 |
| test | 测试相关 |
| chore | 杂项更新 |

### Scope 范围

- `app`: app模块相关的更改
- `lib`: lib模块相关的更改
- `native`: C++/NDK相关的更改
- `deps`: 依赖更新
- `gradle`: Gradle配置

### 示例

```
feat(app): 添加用户登录功能

- 添加登录界面
- 集成登录API
- 添加token存储

Closes #123
```

```
fix(native): 修复JNI崩溃问题

- 修正函数签名
- 添加空指针检查

Fixes #456
```

```
refactor(lib): 重构NativeLib接口

- 分离核心逻辑和UI逻辑
- 简化API设计
```

### 规则

1. 标题不超过50个字符
2. 使用祈使句
3. 标题首字母小写
4. 结尾不加句号
5. Body和Footer可选
6. Footer使用 `Closes #issue` 或 `Fixes #issue`
7. 提交内容里面不要带上AI信息

## 构建命令

```bash
# 调试构建
./gradlew assembleDebug

# 清理构建
./gradlew clean assembleDebug

# 依赖更新
./gradlew dependencies
```
