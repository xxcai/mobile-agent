# Mobile Agent - 规范文档

## 技术规范
参考[codebase](.planning/codebase)

## 交互规范
1. 交付文档/CLI交互/代码注释尽量使用中文，特有技术名词允许使用英文

## GSD `.planning` 文档规范
1. 默认milestones版本号格式为`1.x/2.x`，下一个里程碑默认`x + 1`；比如当前版本是`2.10`，下个版本是`2.11`
2. 每个milestones，phase的编号从1开始
3. phase直接存放在类似这样的路径`.planning/milestones/v2.4-phases`

## GSD debug 任务要求
1. debug 任务必须使用 `goal: find_root_cause_only` 模式
2. 找到根因后，必须先展示分析和修复方案，由我确认后才能修改
3. 禁止使用默认的 find_and_fix 模式自动修复

## GSD quick 任务要求
1. 必须使用 `--discuss --full` 参数
2. 方案生成后，必须先展示计划，由我确认后才能执行
3. 禁止使用不带参数的默认 quick 模式

## 工作流推进
1. 没有我的允许，禁止跳过discuss和plan环节
4. 在verify-work阶段，发生的问题，必须由我确认分析和方案后再进行修改
5. 进行phase查找的时候，先从`ROADMAP.md`中确认我们正在开发哪个版本，去对应版本的milestones目录，查找phases目录