# Mobile Agent - 规范文档

## 技术规范
参考[codebase](.planning/codebase)

## 交互规范
1. 交付文档/CLI交互/代码注释尽量使用中文，特有技术名词允许使用英文

## GSD `.planning` 文档规范
1. 默认milestones版本号格式为`1.x/2.x`，下一个里程碑默认`x + 1`；比如当前版本是`2.10`，下个版本是`2.11`
2. 默认phases内部的目录命名格式为`v<milestones版本号>-<phase编号>-<phase-desc>`，其中`milestones版本号`中的小数点用`_`替换；比如当前版本是`2.10`,生成的第十个phase目录名字格式为`v2_10-10-<phase-desc>`
```
