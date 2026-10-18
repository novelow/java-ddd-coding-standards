# 如何让 AI 一定用这套规范

把规范写对只完成了一半。这一节解决**落地执行**问题。

---

## 一、为什么光有 skill 不够

Skill 的加载机制是「语义匹配」：Agent 读取所有已安装 skill 的 `description`，判断当前任务是否相关，
相关才加载正文。这意味着存在三种典型失效：

| 失效模式 | 表现 |
|---------|------|
| **未命中 description** | 你说"加个批量取消接口"，AI 觉得只是小事，没加载 skill，直接按习惯写 |
| **模仿存量代码** | AI 读到的上下文里全是仓库里的旧代码，认为"项目风格就这样"，照着坏例子写 |
| **主文件过长被忽略** | SKILL.md 超过 300 行后，后半段规则的遵循率明显下降 |

下面三种挂载方式按**可靠性从高到低**排列，建议组合使用。

---

## 二、方式 A：项目级目录 + 根 AGENT.md 强引用（推荐）

这是唯一能实现"每次都加载"的方式。AGENT.md 是每次会话自动读取的项目约定文件，
在它里面写死"必须先读 skill"，就把「语义匹配」变成了「强制规则」。

```bash
cd /你的项目根目录

# 1. 克隆到项目级 skills 目录
mkdir -p .workbuddy/skills
git clone <仓库地址> .workbuddy/skills/java-ddd-coding-standards

# 2. 复制 AGENT.md 模板到项目根
cp .workbuddy/skills/java-ddd-coding-standards/integration/AGENT.md ./AGENT.md

# 3. 替换占位符为项目内的相对路径
sed -i '' 's#{SKILL_PATH}#.workbuddy/skills/java-ddd-coding-standards#g' ./AGENT.md
```

不同工具对应的路径：

| 工具 | skills 目录 | 根约定文件 |
|------|------------|-----------|
| WorkBuddy | `.workbuddy/skills/` | `AGENT.md` |
| Claude Code | `.claude/skills/` | `CLAUDE.md`（用 `CLAUDE.md` 命名同样文件） |
| Cursor | — | `.cursor/rules/xxx.mdc` |

如果项目根目录**已经有** `AGENT.md`：把本模板的一到五节内容**追加**进去，
或者用一行引用代替：

```markdown
## 编码规范

Java 后端代码必须遵守 [SKILL.md](.workbuddy/skills/java-ddd-coding-standards/SKILL.md)，
开始前必读，产出后必须自检。
```

---

## 三、方式 B：用户级目录（对所有项目生效）

```bash
git clone <仓库地址> ~/.workbuddy/skills/java-ddd-coding-standards
```

优点：一次安装，所有项目可用。
缺点：**依赖 description 语义匹配，存在漏加载**。建议同时提升 description 命中率——
本仓库 `SKILL.md` 的 `description` 已经写满了触发关键词（`@PostMapping`、`@RequestBody`、
Controller、MyBatis、VO、状态机、错误码、重构等），不要为了精简把它改短。

只适合作为方式 A 的补充，不要单独依赖。

---

## 四、方式 C：git submodule（团队统一）

```bash
cd /你的项目根目录
git submodule add <仓库地址> .workbuddy/skills/java-ddd-coding-standards
git submodule update --init --recursive
```

适合多人协作：规范版本随代码仓库一起走，别人 clone 后 `git submodule update --init` 即可对齐。

后续更新规范：

```bash
git -C .workbuddy/skills/java-ddd-coding-standards pull origin main
git add .workbuddy/skills/java-ddd-coding-standards
git commit -m "chore: update coding standards"
```

---

## 五、组合推荐（最省事的一套）

```
1. submodule 或 clone 到 .workbuddy/skills/java-ddd-coding-standards   ← 规范来源，版本可控
2. 项目根放 AGENT.md，强引用该 skill                                     ← 保证每次加载
3. templates/ 的骨架代码进 src/main/java                                 ← 保证有东西可依赖
4. AGENT.md 里声明"存量旧代码是反面教材"                                  ← 阻断模仿坏例子
```

第 2 步和第 4 步是效果最显著的两个，缺了任意一个，执行率都会明显下降。

---

## 六、怎么验证真的生效了

装好后，在**新开一个会话**里让 AI 写一个小接口，例如：

> 加一个批量取消订单的接口，参数是订单号列表

正确的表现：

- 回复中出现了 `Result<Boolean>`、`BatchCancelParam`、`@Valid @RequestBody`
- 没有出现 `@RequestParam("ids") List<String>`
- 回复末尾输出了「规范自检」清单

如果 AI 仍然写成 `@RequestParam List<String>`，按顺序排查：

1. `AGENT.md` 是否在项目根目录、文件名大小写是否正确
2. `{SKILL_PATH}` 占位符是否已替换成真实路径
3. skill 目录下 `SKILL.md` 是否存在（不是 `skill.md`）
4. 是否新开了会话——部分工具只在会话开始时读取约定文件
