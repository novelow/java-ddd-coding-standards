---
name: java-ddd-coding-standards
description: "Java / Spring Boot DDD 分层与接口编码规范。只要任务涉及 Java 后端代码都必须加载：新增或修改 Controller / Service / Domain / Repository、Entity、Mapper；设计或改动 HTTP 接口（@PostMapping、@GetMapping、@RequestBody、@RequestParam、@PathVariable）；定义 Param / Request / Response / VO / DTO 请求响应对象；分层架构与包结构划分；异常处理、ErrorCode、BizException、日志；MyBatis / MyBatis-Plus 持久层、@Transactional、乐观锁；状态机、幂等、Code Review、重构、排查线上问题。强制 Param / Request / Response / VO 四层对象隔离，禁止 POST 请求用 URL query 传递复杂参数，禁止 @Autowired 字段注入。"
description_zh: "Java Spring Boot DDD 架构与接口编码规范"
description_en: "Java Spring Boot DDD architecture & API coding standards"
version: 1.0.0
display_name: "java-ddd-coding-standards"
display_name_en: "java-ddd-coding-standards"
visibility: "public"
---

# Java DDD 编码规范

本规范用于约束 Java / Spring Boot 后端项目的代码编写。**所有规则默认强制执行**，除非项目已有明确且冲突的约定。

适用范围：Spring Boot 单体 / 模块化单体 + MyBatis-Plus。不涉及具体业务领域，不涉及特定认证框架。

---

## 0. 绝对红线（违反即重写）

> 以下 14 条为硬性约束。生成或 Review 代码时，只要命中一条，**必须推倒重写整个类**，不允许局部打补丁。

| # | 约束 | 说明 |
|---|------|------|
| R1 | **NEVER** 用 URL query 或 `@RequestParam` 接收 POST 请求的复杂参数 | 含 List、嵌套对象、枚举集合、或超过 3 个字段。POST 必须 `@RequestBody XxxParam` |
| R2 | **NEVER** 在 Controller 里写业务判断、直接调用 Mapper / Repository | Controller 只做三件事：参数校验、装配 Request、包装 Result |
| R3 | **NEVER** 跨层复用同一个 DTO | Param 只在 Controller 入参，Request 只在服务间入参，Response 只在服务间出参，VO 只作为最终出参 |
| R4 | **NEVER** 使用 `Map` / `JSONObject` / `Object` 作为方法的入参或出参 | 必须定义具体类型 |
| R5 | **NEVER** 吞异常 | `catch` 后必须至少满足其一：记录日志 / 抛出业务异常 / 返回失败结果 |
| R6 | **NEVER** 在循环内查数据库、调用远程接口、发消息 | 必须批量化或先聚合再批量处理 |
| R7 | **NEVER** 用 `if (state == X) { setState(Y); }` 直接改状态 | 状态流转必须走状态机 |
| R8 | **NEVER** 把数据库 Entity 直接返回给前端 | 必须转换为 VO |
| R9 | **NEVER** 在**生产代码**中使用字段注入 `@Autowired` | 必须构造器注入，优先 `@RequiredArgsConstructor`（测试类除外） |
| R10 | **NEVER** 返回 `null` 集合或数组 | 返回空集合。单对象查询允许返回 null，但调用方必须判空 |
| R11 | **NEVER** 让 Domain 层依赖 Spring 注解或基础设施实现 | Domain 层只依赖抽象与领域模型 |
| R12 | **MUST** 所有写操作接口携带幂等标识字段 | 由调用方生成并传递 |
| R13 | **NEVER** 在 Service 方法上无差别使用 `@Transactional` | 只读方法不加事务；事务方法必须显式 `rollbackFor = Exception.class` |
| R14 | **NEVER** 硬编码魔法值 | 状态码、类型、阈值一律提取为枚举或常量 |

---

## 1. 分层职责

| 层 | 包 | 职责 | 允许依赖 | 禁止 |
|---|----|------|---------|------|
| Controller | `controller` | 参数校验、装配 Request、包装 Result | Application / 读接口 Facade | 写业务逻辑、调 Mapper |
| Application | `application` | 业务编排、事务边界、组装领域调用 | Domain、Infrastructure 抽象 | 写核心业务规则 |
| Domain | `domain` | 核心业务规则、实体、状态机、领域事件 | 仅领域模型 | 依赖 Spring、依赖 DB 实现 |
| Infrastructure | `infrastructure` | Mapper、Repository 实现、外部服务适配 | Domain 抽象 | 包含业务规则 |

调用方向严格单向：`Controller → Application → Domain → Infrastructure`。**禁止反向调用，禁止跨层跳跃。**

---

## 2. 四层对象职责（核心）

| 对象 | 后缀 | 诞生位置 | 消费方 | 能否被前端看到 |
|------|------|---------|--------|--------------|
| 请求参数 | `XxxParam` | Controller 入参 | 仅 Controller | 否 |
| 服务入参 | `XxxRequest` | Controller / Application 装配 | Application、Domain | 否 |
| 服务出参 | `XxxResponse` | Domain / Application 返回 | Application、Controller | 否 |
| 视图对象 | `XxxVO` | Controller 装配 | 前端 | 是 |
| 领域实体 | `Xxx`（无后缀） | Domain | Domain、Infrastructure | 否 |

**转换是强制的**：`Param → Request`（Controller 内）、`Entity → VO`（Controller 内）。不允许省略任一步。

---

## 3. 分册索引

按需加载，不要一次性全读：

| 分册 | 何时加载 |
|------|---------|
| `references/api-design.md` | 设计或修改 HTTP 接口、定义出入参、处理参数校验 |
| `references/ddd-layering.md` | 新建模块、划分类的职责、写领域服务、做状态流转 |
| `references/exception-and-logging.md` | 定义错误码、抛异常、加日志 |
| `references/persistence.md` | 写实体、Mapper、Repository、处理并发 |
| `references/review-checklist.md` | 提交代码前自检、做 Code Review |

骨架代码见 `templates/`，可直接复制到新项目（全局替换 `com.yourcompany` 为你的包名）。

---

## 4. 执行要求

生成任何 Java 代码后，**必须**执行以下动作：

1. 加载 `references/review-checklist.md`
2. 逐条核对本次产出的代码
3. 在回复末尾输出核对结果，格式如下：

```
## 规范自检
- R1 POST 复杂参数用 Body：✅ / ❌
- R3 四层对象未串用：✅ / ❌
- ...
- 结论：通过 / 未通过（未通过项已重写）
```

**未通过项必须重写后再输出，不允许直接交付带有 ❌ 的代码。**

---

## 5. 扩展方式

本规范不追求一次性完备。追加规则的推荐做法：

1. 在 `references/` 下新增一个分册（如 `cache.md`、`idempotency.md`）
2. 在 `SKILL.md` 第 3 节的分册索引表中加一行
3. 若新增的是硬性约束，同时在 `SKILL.md` 第 0 节红线表追加一条，并同步更新 `references/review-checklist.md`

**不要**把新规则堆进 `SKILL.md` 正文——主文件超过 300 行后约束力会显著下降。
