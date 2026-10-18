# 骨架代码

可直接复制到新项目的基础类。**复制后第一步：全局替换包名前缀。**

```bash
# 把 com.yourcompany 换成你的实际包名
grep -rl "com.yourcompany" . | xargs sed -i '' 's/com\.yourcompany/com.youractual/g'
```

## 文件清单与放置位置

| 文件 | 建议包名 / 路径 | 说明 |
|------|----------------|------|
| `BaseEntity.java` | `common/base/entity` | 所有 DB 实体的基类，含逻辑删除与乐观锁 |
| `Result.java` | `common/base/response` | HTTP 统一响应包装 |
| `ResponseCode.java` | `common/base/response` | 通用响应码枚举 |
| `PageResult.java` | `common/base/response` | 统一分页出参 |
| `ErrorCode.java` | `common/base/exception` | 错误码接口，所有错误码枚举必须实现 |
| `BizErrorCode.java` | `common/base/exception` | 通用错误码枚举 |
| `BizException.java` | `common/base/exception` | 业务异常基类，各模块继承它定义 XxxException |
| `SimpleErrorCode.java` | `common/base/exception` | 动态错误码，兜底用 |
| `GlobalExceptionHandler.java` | `common/web/handler` | 全局异常处理器 |
| `StateMachine.java` | `common/base/statemachine` | 状态机接口 |
| `BaseStateMachine.java` | `common/base/statemachine` | 状态机基础实现 |

## 依赖前提

- Lombok（`@Getter` / `@Setter` / `@Slf4j`）
- MyBatis-Plus（`BaseEntity` 的 `@TableLogic` / `@Version` / `@TableField`）
- Spring Web（`@RestControllerAdvice`）

## 配套必做项

1. **字段自动填充**：实现 MyBatis-Plus 的 `MetaObjectHandler`，为 `gmtCreate` / `gmtModified` / `deleted` / `lockVersion` 填充值，否则 `BaseEntity` 的自动填充注解不生效。
2. **乐观锁插件**：注册 `OptimisticLockerInnerInterceptor`，否则 `@Version` 不生效。
3. **模块错误码**：每个业务模块建一个 `XxxErrorCode implements ErrorCode` 和一个 `XxxException extends BizException`。
