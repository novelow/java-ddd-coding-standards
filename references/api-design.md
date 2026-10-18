# 接口与 API 设计规范

## 1. 请求参数的传输方式（最高频违规点）

### 1.1 判定规则

| 场景 | 必须使用 | 禁止使用 |
|------|---------|---------|
| POST + 任何写操作 | `@RequestBody XxxParam` | `@RequestParam` / `?a=1&b=2` |
| POST + 参数含 List / 数组 / 嵌套对象 | `@RequestBody XxxParam` | `@RequestParam List<String>` |
| POST + 字段数 > 3 | `@RequestBody XxxParam` | 散落的 `@RequestParam` |
| GET + 简单的单值查询（≤ 3 个基础类型字段） | `@RequestParam` | — |
| GET + 分页 / 多条件筛选查询 | `@ModelAttribute XxxQuery` 或改用 POST + Body | 超过 3 个 `@RequestParam` |
| 路径资源标识 | `@PathVariable` | 把 id 放进 Body |

**判定的唯一标准**：只要参数不是"≤3 个基础类型的简单值"，就必须用 Param 类 + Body。

### 1.2 ❌ / ✅ 对照

```java
// ❌ 错误：POST 用 URL 传 List，URL 会被长度限制截断，且无法表达嵌套结构
@PostMapping("/batchCancel")
public Result<Boolean> batchCancel(@RequestParam("orderIds") List<String> orderIds) { ... }

// ❌ 错误：POST 用一堆 @RequestParam，参数一多就失控，无法做分组校验
@PostMapping("/create")
public Result<String> create(@RequestParam String goodsId,
                             @RequestParam String goodsType,
                             @RequestParam Integer itemCount,
                             @RequestParam String addressId) { ... }

// ❌ 错误：用 Map 接参，等于放弃所有类型安全和校验能力
@PostMapping("/create")
public Result<String> create(@RequestBody Map<String, Object> params) { ... }

// ✅ 正确：POST 一律用 Param 类接收
@PostMapping("/batchCancel")
public Result<Boolean> batchCancel(@Valid @RequestBody BatchCancelParam param) { ... }

// ✅ 正确：简单 GET 查询允许 @RequestParam，但不超过 3 个
@GetMapping("/detail")
public Result<OrderVO> detail(@RequestParam String orderId) { ... }

// ✅ 正确：资源标识用 @PathVariable，其余条件用 Query 类
@GetMapping("/{orderId}/items")
public Result<List<OrderItemVO>> items(@PathVariable String orderId,
                                       @Valid @ModelAttribute OrderItemQuery query) { ... }
```

### 1.3 Param 类写法

```java
@Getter
@Setter
public class BatchCancelParam {

    @NotEmpty(message = "orderIds is empty")
    private List<String> orderIds;

    @NotBlank(message = "operator is blank")
    private String operator;

    private String reason;
}
```

规则：
- 使用 `@Getter` / `@Setter`，**不使用 `@Data`**（避免生成 equals/hashCode 带来的意外行为与循环引用）
- 校验注解必须带 `message`，且 message 使用英文短语（便于国际化与日志检索）
- 嵌套对象字段需要 `@Valid` 才能级联校验
- 不写业务默认值，默认值属于 Domain 层职责

---

## 2. 四层对象隔离

一次完整的请求必须完成三次转换，**不允许省略任何一步**：

```
前端 JSON
   ↓  Spring 反序列化
XxxParam        （Controller 入参，带校验注解）
   ↓  Controller 装配
XxxRequest      （服务入参，可含操作人、操作时间、幂等号等上下文）
   ↓  Domain 处理
XxxResponse     （服务出参，含 success / code / message）
   ↓  Controller 转换
XxxVO           （前端出参，只含前端需要的字段）
```

### 2.1 为什么不能省略

| 省略的后果 | 具体表现 |
|-----------|---------|
| 复用 Param 当 Request | 前端字段变动直接击穿到领域层；校验注解污染领域模型 |
| 复用 Entity 当 VO | 数据库字段（lockVersion、deleted、gmtCreate）泄漏给前端；改表即改接口 |
| 复用 Response 当 VO | 内部响应码语义（如 DUPLICATED_REQUEST）暴露给前端 |

### 2.2 ❌ / ✅ 对照

```java
// ❌ 错误：把 Param 一路传到 Service，甚至传到 Domain
public Result<String> create(@Valid @RequestBody OrderCreateParam param) {
    orderApplicationService.create(param);   // Param 穿透到应用层
}

// ❌ 错误：直接把 Entity 返回给前端
@GetMapping("/{orderId}")
public Result<Order> detail(@PathVariable String orderId) {
    return Result.success(orderRepository.getById(orderId));
}

// ✅ 正确：Controller 内完成 Param → Request → Result<VO> 的完整链路
@PostMapping("/create")
public Result<String> create(@Valid @RequestBody OrderCreateParam param) {
    OrderCreateRequest request = new OrderCreateRequest();
    request.setGoodsId(param.getGoodsId());
    request.setItemCount(param.getItemCount());
    request.setBuyerId(currentUserId());
    request.setIdentifier(param.getIdentifier());   // 幂等号

    OrderCreateResponse response = orderApplicationService.create(request);
    if (!response.getSuccess()) {
        throw new OrderException(response.getResponseCode(), response.getResponseMessage());
    }
    return Result.success(response.getOrderId());
}
```

**装配代码允许冗长。** 显式 setter 优于 BeanUtils 反射拷贝——反射拷贝在字段改名时不会报错，是隐蔽的线上事故源。

---

## 3. Controller 层约束

Controller 方法体只允许包含以下动作，出现其他内容即为违规：

1. 接收并校验 Param（`@Valid`）
2. 从上下文获取操作人（登录态）
3. 装配 Request
4. 调用 Application 服务
5. 将 Response 转为 `Result<VO>` 返回

### ❌ 违规示例

```java
// ❌ Controller 里做业务判断
@PostMapping("/pay")
public Result<Boolean> pay(@Valid @RequestBody PayParam param) {
    Order order = orderMapper.selectById(param.getOrderId());   // 直接调 Mapper
    if (order.getState() != CONFIRM) {                          // 业务规则泄漏到 Controller
        return Result.error("ORDER_CANNOT_PAY", "订单不可支付");
    }
    if (order.getAmount().compareTo(param.getAmount()) != 0) {  // 金额校验
        return Result.error("AMOUNT_ERROR", "金额不一致");
    }
    ...
}
```

### ✅ 正确示例

```java
@PostMapping("/pay")
public Result<PayVO> pay(@Valid @RequestBody PayParam param) {
    PayRequest request = new PayRequest();
    request.setOrderId(param.getOrderId());
    request.setPayerId(currentUserId());
    request.setPayChannel(param.getPayChannel());
    request.setIdentifier(param.getIdentifier());

    PayResponse response = payApplicationService.pay(request);

    if (!response.getSuccess()) {
        throw new BizException(response.getResponseCode(), response.getResponseMessage());
    }
    return Result.success(PayConvertor.toVO(response));
}
```

规则：
- Controller **不写 try-catch**，异常统一交给全局异常处理器（见 `exception-and-logging.md`）
- Controller 不注入 Mapper / Repository
- 一个 Controller 对应一个业务聚合，方法数建议不超过 10 个

---

## 4. 统一响应格式

所有 HTTP 接口返回统一的 `Result<T>`：

```java
public class Result<T> {
    private String code;       // 业务码，成功固定为 "SUCCESS"
    private Boolean success;
    private String message;
    private T data;
}
```

规则：
- 成功：`Result.success(data)`，**不返回额外的 success 判断给前端，靠 HTTP 状态码 + code 字段**
- 失败：不允许在 Controller 内 `return Result.error(...)` 兜底所有异常——应抛出业务异常，由全局处理器统一转成 `Result`
- 分页出参使用 `PageResult<T>`，字段名固定为 `total` / `list` / `pageNum` / `pageSize`
- 返回给前端的时间字段统一为 `Long` 时间戳或 `yyyy-MM-dd HH:mm:ss` 字符串，二选一，全项目一致

### ❌ / ✅ 对照

```java
// ❌ 错误：每个方法都 try-catch 然后手动返回 error，异常信息在两层被吞掉
try {
    ...
} catch (Exception e) {
    log.error(e.getMessage());                    // 只打 message，没有堆栈
    return Result.error("SYS_ERROR", "系统异常");  // 真实原因丢失
}

// ✅ 正确：直接抛，全局处理器负责转换与记录
throw new OrderException(OrderErrorCode.ORDER_CANNOT_PAY);
```

---

## 5. 接口命名与路径

| 操作 | HTTP 方法 | 路径示例 | 方法名 |
|------|----------|---------|--------|
| 查询单个 | GET | `/orders/{orderId}` | `detail` |
| 分页查询 | POST | `/orders/search` | `search` |
| 创建 | POST | `/orders` | `create` |
| 更新 | PUT | `/orders/{orderId}` | `update` |
| 局部更新 | PATCH | `/orders/{orderId}/state` | `updateState` |
| 删除 | DELETE | `/orders/{orderId}` | `delete` |
| 业务动作 | POST | `/orders/{orderId}/cancel` | `cancel` |

规则：
- 路径使用小写复数名词，多单词用 `-` 连接（`/order-items`）
- `@RequestMapping` 不写斜杠前缀：`@RequestMapping("orders")` 而非 `"/orders"`
- 业务动作（取消、支付、审核）用 `POST /资源/{id}/动作`，不要用 `POST /cancelOrder` 这种 RPC 风格
- 分页查询用 POST + Body，避免筛选条件撑爆 URL

---

## 6. 幂等设计

所有写接口必须接收 `identifier` 字段（幂等号），由调用方生成：

- 前端发起写操作前先向服务端申请幂等号，或使用业务唯一键组合
- 服务端在入口层基于 `identifier` 做去重，重复请求直接返回首次结果
- 领域层的写操作基于 `identifier` + 业务唯一键建立唯一索引兜底

```java
// ✅ Request 中固定携带幂等号
public class OrderCreateRequest {
    private String identifier;   // 幂等号，必填
    ...
}
```
