# 异常与日志规范

## 1. 错误码体系

### 1.1 三层错误码

| 层级 | 位置 | 示例 | 用途 |
|------|------|------|------|
| 通用错误码 | 公共模块 `ErrorCode` 枚举 | `PARAM_ERROR`、`SYS_ERROR`、`STATE_TRANSITION_FAILED` | 全项目通用 |
| 模块错误码 | 各模块 `XxxErrorCode` | `ORDER_NOT_EXIST`、`ORDER_CANNOT_PAY` | 模块内业务失败 |
| 持久化错误码 | 公共模块 | `DB_OPERATE_FAILED` | 数据访问失败 |

### 1.2 错误码定义

```java
@Getter
@AllArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_EXIST("ORDER_NOT_EXIST", "订单不存在"),
    ORDER_CANNOT_PAY("ORDER_CANNOT_PAY", "订单当前状态不可支付"),
    ORDER_CREATE_FAILED("ORDER_CREATE_FAILED", "订单创建失败");

    private final String code;
    private final String message;
}
```

```java
public interface ErrorCode {
    String getCode();
    String getMessage();
}
```

规则：
- 错误码全大写 + 下划线，格式为 `模块_具体错误`，全局唯一
- **必须实现统一的 `ErrorCode` 接口**，便于全局处理器统一处理
- message 面向用户，用中文；需要国际化的项目改为 message key
- 禁止使用数字错误码（如 `50001`），可读性差且难检索

### 1.3 模块私有异常

```java
@Getter
public class OrderException extends RuntimeException {

    private final ErrorCode errorCode;

    public OrderException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public OrderException(String code, String message) {
        super(message);
        this.errorCode = new SimpleErrorCode(code, message);
    }
}
```

规则：
- 每个业务模块定义自己的 `XxxException`，继承 `RuntimeException`
- 异常必须携带 `ErrorCode`，不允许只传一个字符串
- 禁止直接抛 `RuntimeException`、`Exception`、`IllegalArgumentException` 来表达业务失败

---

## 2. 异常使用规则

### 2.1 抛出

```java
// ✅ 正确：抛带错误码的业务异常
throw new OrderException(OrderErrorCode.ORDER_NOT_EXIST);

// ✅ 正确：参数断言
Assert.isTrue(order.payable(), () -> new OrderException(OrderErrorCode.ORDER_CANNOT_PAY));

// ❌ 错误：裸抛，全局处理器无法给出明确错误码
throw new RuntimeException("订单不存在");

// ❌ 错误：用异常控制正常流程
try {
    return repository.getById(id);
} catch (Exception e) {
    return null;   // 把异常吞掉返回 null
}
```

### 2.2 捕获

捕获异常后**必须**满足以下至少一项，否则视为吞异常（红线 R5）：

1. 记录日志（`log.error("...", e)`，必须带异常对象）
2. 包装为业务异常继续抛出
3. 转换为失败响应返回

```java
// ❌ 错误：三种吞异常的典型形态
catch (Exception e) { }                          // 空 catch
catch (Exception e) { log.error(e.getMessage()); }  // 只打 message，丢失堆栈
catch (Exception e) { return null; }             // 静默降级，调用方无法感知

// ✅ 正确：记录完整堆栈 + 转换为业务语义
catch (OrderException e) {
    log.warn("order pay rejected, orderId={}, code={}", orderId, e.getErrorCode().getCode());
    throw e;
}
catch (Exception e) {
    log.error("order pay failed, orderId={}", orderId, e);
    throw new OrderException(OrderErrorCode.ORDER_PAY_FAILED);
}
```

规则：
- catch 块中日志必须传异常对象 `e` 作为最后一个参数（SLF4J 会打印完整堆栈）
- 不要捕获后原样抛出又不做任何事，那不如不 catch
- 业务异常用 `log.warn`，系统异常用 `log.error`

### 2.3 全局异常处理

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("biz exception, code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return Result.error(BizErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("system exception", e);
        return Result.error(BizErrorCode.SYS_ERROR.getCode(), BizErrorCode.SYS_ERROR.getMessage());
    }
}
```

规则：
- Controller 内**不写 try-catch**，全部交给 `@RestControllerAdvice`
- 兜底处理器返回通用错误码，不把异常堆栈信息返回给前端
- 参数校验失败要聚合所有字段错误，不要只返回第一个

---

## 3. 日志规范

### 3.1 级别

| 级别 | 使用场景 |
|------|---------|
| ERROR | 系统异常、不可恢复失败、需要人工介入 |
| WARN | 业务异常、重试、降级、潜在问题 |
| INFO | 关键业务流程节点、状态变更、外部调用结果 |
| DEBUG | 调试信息，默认关闭 |

### 3.2 必须记录的字段

每条业务日志必须带上可定位的**业务标识**，否则线上无法排查：

```java
// ❌ 错误：没有业务标识，出问题无法定位到具体单据
log.info("order created");

// ✅ 正确：带 orderId 与 buyerId
log.info("order created, orderId={}, buyerId={}, amount={}",
         order.getOrderId(), order.getBuyerId(), order.getAmount());
```

必备字段（按场景选）：`bizId`（订单号/单据号）、`userId`、`identifier`（幂等号）、`state`（状态前后值）。

### 3.3 写法约束

- 使用 SLF4J + Lombok `@Slf4j`，禁止 `System.out.println`
- 必须使用占位符 `{}`，**禁止字符串拼接**
- 占位符数量必须与参数数量一致
- 日志内容不含敏感信息（手机号、身份证、密码、token），必要时脱敏

```java
// ❌ 错误：字符串拼接，且未脱敏
log.info("user login, mobile=" + mobile + ", token=" + token);

// ✅ 正确：占位符 + 脱敏
log.info("user login, userId={}, mobile={}", userId, desensitize(mobile));
```

### 3.4 日志位置

| 位置 | 要求 |
|------|------|
| Controller 入口 | 不强制，由全局切面或 Filter 统一记录 |
| Application 方法入口/出口 | 关键业务必须记录，含耗时 |
| 状态变更 | 必须记录变更前后的状态值 |
| 外部系统调用 | 必须记录请求与响应摘要 + 耗时 |
| 异常分支 | 必须记录，带异常对象 |

---

## 4. 断言与参数校验

- Controller 入参：`@Valid` + JSR-303 注解（`@NotBlank`、`@NotNull`、`@NotEmpty`、`@Min`、`@Size`）
- 领域层内部前置条件：使用断言，失败即抛异常，不做静默兜底
- 禁止在 Domain 层返回"成功但没有数据"来掩盖校验失败
