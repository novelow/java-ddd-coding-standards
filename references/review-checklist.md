# 代码自检清单

生成或修改 Java 代码后，**必须逐条核对并以如下格式输出核对结果**。任何一项 ❌ 都必须先重写再交付。

## 自检输出模板

```
## 规范自检
### 红线
- R1 POST 复杂参数用 @RequestBody + Param 类：✅ / ❌ / N/A
- R2 Controller 无业务逻辑、不调 Mapper：✅ / ❌ / N/A
- R3 Param / Request / Response / VO 未串用：✅ / ❌ / N/A
- R4 无 Map / JSONObject / Object 作为出入参：✅ / ❌ / N/A
- R5 无吞异常（空 catch / 只打 message / 静默返回）：✅ / ❌ / N/A
- R6 无循环内查库、调远程、发消息：✅ / ❌ / N/A
- R7 状态流转走状态机，无 if-else 直接赋值：✅ / ❌ / N/A
- R8 Entity 未直接返回前端：✅ / ❌ / N/A
- R9 生产代码构造器注入，无 @Autowired 字段注入：✅ / ❌ / N/A
- R10 集合返回空集合而非 null：✅ / ❌ / N/A
- R11 Domain 层无 Spring 注解与基础设施依赖：✅ / ❌ / N/A
- R12 写接口携带幂等号 identifier：✅ / ❌ / N/A
- R13 @Transactional 显式 rollbackFor，只读不加事务：✅ / ❌ / N/A
- R14 无魔法值，状态/类型用枚举或常量：✅ / ❌ / N/A

### 质量项
- 命名符合规范（Controller/Application/Domain/Param/VO/Convertor）：✅ / ❌ / N/A
- 日志使用占位符且带业务标识（bizId / userId）：✅ / ❌ / N/A
- 异常携带 ErrorCode，未裸抛 RuntimeException：✅ / ❌ / N/A
- 分页查询有 pageSize 上限与显式排序：✅ / ❌ / N/A
- 无 SELECT *：✅ / ❌ / N/A
- 事务边界在 Application 层：✅ / ❌ / N/A

- 结论：通过 / 未通过（列出未通过项并重写）
```

---

## 逐项判定说明

### R1 POST 复杂参数

- 检查所有 `@PostMapping`：参数是否都是 `@RequestBody` + `XxxParam`
- 出现以下任一即为 ❌：`@RequestParam List<...>`、`@RequestParam` 超过 3 个、`Map` 接参

### R2 Controller 职责

- Controller 方法体内不应出现：`if` 业务判断、`Mapper`、`Repository`、金额/状态比较
- 唯一允许的条件判断是 `if (!response.getSuccess())` 这种响应判定

### R3 对象隔离

- `Param` 不得出现在 Application / Domain 的签名里
- `Request` 不得出现在 Controller 的返回里
- `Entity` 不得出现在 Controller 的返回里
- `VO` 不得出现在 Domain 的签名里

### R4 类型安全

- 方法签名中搜索 `Map<`、`JSONObject`、`Object ` 作为参数或返回类型
- 允许的例外：Convertor 内部临时变量

### R5 异常

- 搜索 `catch`，逐个检查是否至少满足「记日志 / 抛异常 / 返回失败」之一
- 检查 `log.error(e.getMessage())` 这类丢失堆栈的写法

### R6 循环内 IO

- 检查 `for` / `forEach` / `while` 内是否出现：Mapper 调用、Repository 调用、HTTP 调用、消息发送

### R7 状态机

- 搜索 `set.*State(`、`setStatus(`，确认外围是否有 `if (... == XxxState.Y)` 直接赋值
- 正确的写法是实体方法内部调用 `XxxStateMachine.INSTANCE.transition(...)`

### R9 依赖注入

检查范围：**仅生产代码**（`src/main/java`），测试类不检查。

- 搜索 `@Autowired` 是否出现在**字段**上（出现在构造器或 setter 上是允许的）
- 确认服务类使用 `@RequiredArgsConstructor` + `private final` 字段
- 确认没有被 `@Lazy` 用来"绕过"循环依赖——循环依赖应当重构拆分，不应被掩盖

常见漏网形态：

```java
// ❌ 字段注入
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
}

// ❌ @Lazy 掩盖循环依赖
@Service
public class OrderService {
    public OrderService(@Lazy InventoryService inventoryService) { ... }
}

// ✅ 构造器注入
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
}
```

### R13 事务

- 搜索 `@Transactional`，确认：不在 Controller、有 `rollbackFor`、方法非纯查询、方法体无远程调用

---

## 常见误判提醒

| 现象 | 判定 |
|------|------|
| GET 单值查询用 `@RequestParam` | ✅ 允许，标记 N/A |
| Convertor 里出现 `Map` 做中间分组 | ✅ 允许，红线 R4 只约束方法签名 |
| 单对象查询返回 null | ✅ 允许，但调用方必须判空 |
| Domain 实体上有 `@TableName` | ✅ 允许，这属于持久化映射不是 Spring 容器注解 |
| Application 层有 `if` 做流程分支 | ✅ 允许，R2 只约束 Controller |
| **测试类**（`*Test` / `*IT`）用 `@Autowired` 字段注入 | ✅ 允许，标记 N/A。红线 R9 只约束生产代码 |
| `@Bean` 方法的参数注入 | ✅ 允许，这是方法注入不是字段注入 |
| `@ConfigurationProperties` 配置类有 setter | ✅ 允许，配置绑定需要 setter，该类可豁免 R9 |
