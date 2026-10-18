# java-ddd-coding-standards

一套用于约束 AI 编写 Java 后端代码的架构与编码规范（Skill）。

面向 **Spring Boot 单体 / 模块化单体 + MyBatis-Plus**。内容已做通用化处理，不包含任何具体项目的包名、模块名或业务语义。

## 解决什么问题

没有规范约束时，AI 生成的代码通常会：

- 把 POST 请求的 `List` 参数塞进 URL query（`?ids=a&ids=b`），URL 长度受限且无法表达嵌套结构
- 用 `Map<String, Object>` 接参，彻底放弃类型安全与校验
- 一个 DTO 从 Controller 一路传到 Mapper，前端改字段直接击穿到领域层
- Controller 里堆满业务判断，还顺便注入了 Mapper
- 状态流转写成 `if (state == X) setState(Y)`，加一个新状态要改十处
- 数据库 Entity 直接 `return` 给前端，`lockVersion`、`deleted` 全部泄漏
- `catch (Exception e) { }` 空着，线上出问题查不到任何线索

本规范用 **14 条绝对红线 + 正反例代码对照 + 强制自检清单** 来堵住这些口子。

## 目录结构

```
java-ddd-coding-standards/
├── README.md                            # 本文件
├── SKILL.md                             # 主文件：14 条红线 + 分层表 + 对象职责表 + 分册索引
├── integration/                         # 落地挂载方案（怎么让 AI 真的遵守）
│   ├── README.md                        # 三种挂载方式 + 可靠性排序 + 验证方法
│   └── AGENT.md                         # 可直接放到项目根目录的强制触发模板
├── references/
│   ├── api-design.md                    # 接口设计：参数传输方式、四层对象隔离、Controller 约束、统一响应、幂等
│   ├── ddd-layering.md                  # DDD 分层：包结构、各层职责、充血模型、状态机、依赖方向
│   ├── exception-and-logging.md         # 异常与日志：错误码体系、模块私有异常、全局处理器、日志字段
│   ├── persistence.md                   # 持久层：Mapper/Repository 分层、实体基类、乐观锁、N+1、事务边界
│   └── review-checklist.md              # 自检清单：逐条判定说明 + 输出模板 + 常见误判提醒
└── templates/                           # 可直接复制的骨架代码（包名前缀 com.yourcompany）
    ├── BaseEntity.java
    ├── Result.java
    ├── ResponseCode.java
    ├── PageResult.java
    ├── ErrorCode.java
    ├── BizErrorCode.java
    ├── BizException.java
    ├── SimpleErrorCode.java
    ├── GlobalExceptionHandler.java
    ├── StateMachine.java
    ├── BaseStateMachine.java
    └── README.md
```

## 安装

> ⚠️ **只 clone 到 skills 目录是不够的。** Skill 靠语义匹配加载，存在漏加载，
> 必须同时在项目根目录放 `AGENT.md` 强制引用它，否则 AI 大概率还是按自己习惯写。

### 三步走（推荐）

```bash
cd /你的项目根目录

# 1. 规范来源（版本可控，团队可对齐）
git submodule add <your-repo-url> .workbuddy/skills/java-ddd-coding-standards

# 2. 强制触发：复制 AGENT.md 模板到项目根并替换占位符
cp .workbuddy/skills/java-ddd-coding-standards/integration/AGENT.md ./AGENT.md
sed -i '' 's#{SKILL_PATH}#.workbuddy/skills/java-ddd-coding-standards#g' ./AGENT.md

# 3. 骨架代码进源码树（全局替换包名）
cp -r .workbuddy/skills/java-ddd-coding-standards/templates ./docs/_skeleton
# 把 templates 下的类放到你的 common 包下，并替换 com.yourcompany → 你的包名
```

完整说明、三种挂载方式的可靠性对比、以及「装完怎么验证真的生效」见
**[`integration/README.md`](integration/README.md)**。

### 其他安装位置

| 场景 | 命令 | 说明 |
|------|------|------|
| 用户级（所有项目） | `git clone <url> ~/.workbuddy/skills/java-ddd-coding-standards` | 依赖语义匹配，**需配合 AGENT.md 使用** |
| Claude Code | `git clone <url> .claude/skills/java-ddd-coding-standards` | 根目录约定文件改名为 `CLAUDE.md` |
| 只当文档用 | 复制 `references/` 到项目 `docs/` | 人工查阅，无强制力 |

### 只作为规范文档使用

直接把 `references/` 下的分册丢给 AI，或复制到项目的 `docs/` 目录供人查阅。

## 开始使用

1. 复制 `templates/` 下的骨架代码到新项目，**全局替换 `com.yourcompany` 为你的包名**（命令见 `templates/README.md`）
2. 按 `templates/README.md` 的「配套必做项」注册 MyBatis-Plus 的字段填充与乐观锁插件
3. 每个业务模块建一个 `XxxErrorCode implements ErrorCode` 和一个 `XxxException extends BizException`
4. **把 `integration/AGENT.md` 放到项目根目录并替换 `{SKILL_PATH}`** —— 这一步决定了 AI 会不会真的遵守
5. 验收：新开一个会话，让 AI 写「参数为订单号列表的批量取消接口」，
   正确结果应该是 `@Valid @RequestBody BatchCancelParam` 而不是 `@RequestParam List<String>`，
   且回复末尾带规范自检清单

## 14 条绝对红线（摘要）

| # | 约束 |
|---|------|
| R1 | POST 复杂参数必须用 `@RequestBody XxxParam`，禁止 URL query |
| R2 | Controller 禁止业务判断、禁止直接调 Mapper |
| R3 | Param / Request / Response / VO 禁止串用 |
| R4 | 禁止 `Map` / `JSONObject` / `Object` 作为出入参 |
| R5 | 禁止吞异常 |
| R6 | 禁止循环内查库 / 调远程 / 发消息 |
| R7 | 状态流转必须走状态机 |
| R8 | Entity 禁止直接返回前端 |
| R9 | 生产代码禁止 `@Autowired` 字段注入，必须构造器注入（测试类除外） |
| R10 | 禁止返回 null 集合 |
| R11 | Domain 层禁止依赖 Spring 与基础设施 |
| R12 | 写接口必须携带幂等号 |
| R13 | `@Transactional` 必须显式 `rollbackFor`，只读不加事务 |
| R14 | 禁止硬编码魔法值 |

完整判定说明与例外情况见 `references/review-checklist.md`。

## 如何追加规则

规范不追求一次性完备。追加规则的推荐做法：

1. 在 `references/` 下新增分册（如 `cache.md`、`idempotency.md`、`unit-test.md`）
2. 在 `SKILL.md` 的分册索引表加一行
3. 如果是硬性约束：在 `SKILL.md` 红线表追加一条，并同步更新 `references/review-checklist.md`

**不要把新规则堆进 `SKILL.md` 正文** —— 主文件超过 300 行后，AI 对后半段规则的遵循率会显著下降。

## 有意排除的内容

以下内容**故意不纳入**本规范，以保持通用性：

- 认证鉴权框架（Sa-Token、Spring Security 等）—— 各项目选型差异大
- RPC 框架（Dubbo、Feign）—— 单体项目不需要，需要时请自行新增分册
- 分布式事务（TCC、Saga）—— 单体项目不需要
- 分库分表、特定 ORM 高级特性
- SQL 编写与表结构规范 —— 各项目数据库差异过大，不做统一约束

如果你的项目需要这些，按上面的方式新增分册即可。

## 许可

MIT
