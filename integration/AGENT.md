# 项目编码约定

> 本文件放在**项目根目录**。把下面占位符 `{SKILL_PATH}` 替换成实际的 skill 路径后提交到仓库。

`{SKILL_PATH}` 示例：

- `.workbuddy/skills/java-ddd-coding-standards`（项目级，推荐）
- `.claude/skills/java-ddd-coding-standards`
- `~/.workbuddy/skills/java-ddd-coding-standards`（用户级）

---

## 一、强制触发（最高优先级）

本项目**所有** Java 后端代码必须遵守 `{SKILL_PATH}/SKILL.md` 定义的架构与编码规范。

在开始下列**任何一类**任务之前，你**必须先**读取 `{SKILL_PATH}/SKILL.md`，
再按该文件中「分册索引」一节只加载与当前任务相关的分册：

- 新增或修改 `Controller` / `Service` / `Entity` / `Mapper` / `Repository`
- 设计或调整 HTTP 接口（`@PostMapping` / `@GetMapping` 等）
- 定义请求参数类、响应对象、VO、DTO
- 处理异常、定义错误码、加日志
- 编写数据库访问代码、处理事务与并发
- 做 Code Review、重构、排查线上问题

**不允许**在不读规范的情况下直接开工，也不允许依据"我以前的经验"推断本项目的写法。

---

## 二、不可协商的红线

以下约束优先级最高，即使任务描述没有明确要求，也不得违反：

| # | 约束 |
|---|------|
| R1 | POST 请求的复杂参数必须用 `@RequestBody XxxParam`，禁止放进 URL query |
| R2 | Controller 禁止业务判断、禁止直接注入 Mapper |
| R3 | Param / Request / Response / VO / Entity 禁止跨层串用 |
| R4 | 禁止 `Map` / `JSONObject` / `Object` 作为方法出入参 |
| R5 | 禁止吞异常（空 catch、只打 message、静默返回 null） |
| R6 | 禁止在循环内查库 / 调远程 / 发消息 |
| R7 | 状态流转必须走状态机，禁止 if-else 直接给状态字段赋值 |
| R8 | Entity 禁止直接返回给前端，必须转 VO |
| R9 | 生产代码禁止 `@Autowired` 字段注入，必须构造器注入 |
| R10 | 集合返回值禁止为 null |
| R11 | Domain 层禁止依赖 Spring 注解与基础设施实现 |
| R12 | 写接口必须携带幂等号 `identifier` |
| R13 | `@Transactional` 必须显式 `rollbackFor = Exception.class`，只读方法不加事务 |
| R14 | 禁止硬编码魔法值，状态与类型一律用枚举或常量 |

---

## 三、重要：不要模仿仓库里的旧代码

**本仓库的存量代码可能早于本规范，属待治理对象，不是参考范例。**

具体执行的三条判断原则：

1. 当看到现有代码违反上面的红线时，**不要**认为"项目里就是这么写的，所以是对的"
2. 修改一个已有文件时，**不要**沿用该文件原有的违规写法来保持"风格一致"
3. 规范与存量代码冲突时，**一律以 `{SKILL_PATH}` 规范文档为准**

如果发现大面积存量代码违规，可以提出，但不要自行发起超出当前任务范围的批量重构。

---

## 四、产出要求

生成或修改任何 Java 代码后，**必须**执行：

1. 加载 `{SKILL_PATH}/references/review-checklist.md`
2. 逐条核对本次产出的代码
3. 在回复末尾输出自检结果（模板见该文件的「自检输出模板」一节）

存在 ❌ 项时必须先重写再交付，**不允许直接把带有 ❌ 的代码交付给用户**。

---

## 五、优先级声明

本规范的优先级**高于**：

- 模型自身的训练习惯与偏好写法
- 从本仓库存量代码推断出的所谓"项目风格"
- 网络上的通用最佳实践文章

唯一例外：用户在本轮对话中明确指出"这个规则不适用 / 不用管这条"，此时以用户当轮指示为准。
