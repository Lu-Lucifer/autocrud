---
layout: default
title: Team Prompts
nav_order: 14
permalink: /custom/team-prompts
---

> Discover new ways to collaborate and share your brilliance with your team.

默认的 Team Prompts 路径是： `prompts/`，即项目根目录下的 `prompts/` 目录。

AutoDev 当前采用的是 Apache Velocity 模板引擎，可以通过修改模板文件来定制自己的 Team Prompts。

{: .warning }
如果你修改了模板文件，需要确保保存，否则 IDE 无法识别到你的修改。

示例：

```vtl
---
interaction: AppendCursorStream
---
```user```

你是一个资深的软件开发工程师，你擅长使用 TDD 的方式来开发软件，你现在需要帮助帮手开发人员做好 Tasking，以方便于编写测试用例。

- Tasking 产生的任务都是具有独立业务价值的，每完成一条，都可以独立交付、产生价值。
- 采用面向业务需求的 Tasking 采用业务语言描述任务列表，更有助于开发人员和业务人员对需求进行详细的沟通和确认。
- 采用 Given When Then 的书写格式，其中 When 中所代表系统行为。
- 要考虑业务场景覆盖率，尽可能考虑到边界条件

请严格按照以下的格式输出。

示例如下：

Question: 开发一个出租车计费功能，它的计算规则是这样的：不超过8公里时每公里收费0.8元，超过8公里则每公里加收50%长途费，停车等待时每分钟加收0.25元。
Answer: ###
${commentSymbol} Given 出租车行驶了5公里（8公里以内），未发生等待，When 计费，Then 收费4元
${commentSymbol} Given 出租车行驶了5公里（8公里以内），等待10分钟，When 计费，Then 收费6.5元
${commentSymbol} Given 出租车恰好行驶了8公里，未发生等待，When 计费，Then 收费6.4元
${commentSymbol} Given 出租车恰好行驶了8公里，等待10分钟，When 计费，Then 收费8.9元
###
Question: ${selection}
Answer: ###
```

更多的变量可以参考 [AutoDev 模板变量](/variables)。
