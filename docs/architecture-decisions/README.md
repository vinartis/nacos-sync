# Architecture Decision Records (ADR)

本目录记录 nacos-sync 项目的架构决策。每份 ADR 描述一个重要的架构或技术决策的背景、备选方案、最终决定及其影响。

## ADR 列表

| 编号 | 标题 | 状态 | 日期 |
|------|------|------|------|
| [ADR-001](ADR-001-upgrade-nacos-client-to-3x.md) | Upgrade nacos-client to 3.x | Accepted | 2026-06-29 |

## ADR 模板

```markdown
# ADR-XXX: <决策标题>

- **Status**: Proposed | Accepted | Deprecated | Superseded
- **Date**: YYYY-MM-DD
- **Decision Maker**: <决策者>
- **Supersedes**: <被替代的 ADR 编号，无则填 None>
- **Superseded by**: <替代本 ADR 的编号，无则填 None>

## Context
<问题背景、触发因素、约束条件>

## Decision Drivers
<驱动决策的关键因素>

## Considered Options
<考虑过的备选方案，每个方案的 Pros/Cons>

## Decision
<最终决定及理由>

## Consequences
- Positive: <正面影响>
- Negative: <负面影响>
- Neutral: <中性影响>

## Risks
<风险与缓解措施>

## Validation
<验证方式与结果>

## References
<相关资料链接>
```

## 编写规范

- **何时写 ADR**：当决策满足以下任一条件时编写
  - 引入、替换或移除重要依赖
  - 改变系统架构或核心模块的交互方式
  - 涉及多个备选方案的权衡
  - 影响外部用户的兼容性

- **文件命名**：`ADR-XXX-kebab-case-title.md`，编号连续递增

- **状态流转**：
  - `Proposed` → `Accepted`（批准实施）
  - `Accepted` → `Deprecated`（废弃但保留记录）
  - `Accepted` → `Superseded by ADR-YYY`（被新 ADR 替代）

- **不可变**：一旦 Accepted，ADR 内容不再修改。后续变更应创建新 ADR 并在 `Superseded by` 字段引用。
