# 持久层规范

> 本册只约定**代码层面的持久层写法**。表结构、字段命名、DDL、SQL 编写规范不在本册范围内
> （各项目数据库差异较大，不做统一约束）。如后续需要，请单独新增分册。

## 1. 分层：Mapper / Repository

| 组件 | 位置 | 职责 | 谁可以调用 |
|------|------|------|-----------|
| Mapper | `infrastructure/mapper` | 单表 CRUD，继承 MyBatis-Plus `BaseMapper` | **仅 Repository** |
| Repository | `infrastructure/repo` | 封装查询条件、组合多表、对外提供领域语义方法 | Domain / Application |

规则：
- **Application 与 Domain 禁止直接注入 Mapper**，必须经过 Repository
- Repository 方法名使用领域语义（`findPayableOrders`），不要用 SQL 语义（`selectByStateAndTime`）
- 复杂查询（多表关联、动态条件）写在 Mapper XML 或 `@Select` 中，不在 Service 里拼条件

```java
// ❌ 错误：Application 直接注入 Mapper，越层
@Service
public class OrderApplicationService {
    private final OrderMapper orderMapper;
}

// ✅ 正确：依赖 Repository 抽象
@Service
public class OrderApplicationService {
    private final OrderRepository orderRepository;
}
```

---

## 2. 实体规范

### 2.1 统一基类

所有数据库实体继承 `BaseEntity`（见 `templates/BaseEntity.java`），统一携带：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键，自增 |
| `deleted` | Integer | 逻辑删除，`@TableLogic` |
| `lockVersion` | Integer | 乐观锁版本号，`@Version` |
| `gmtCreate` | Date | 创建时间，插入时自动填充 |
| `gmtModified` | Date | 修改时间，插入与更新时自动填充 |

规则：
- 实体使用 `@Getter` / `@Setter`，**不使用 `@Data`**
- 实体不允许有 `@TableName` 之外的 Spring 注解
- 实体字段与表字段一一对应，不允许出现非持久化的中间字段（需要则用 `@TableField(exist = false)` 并注明）

### 2.2 乐观锁

并发更新必须基于 `lockVersion`：

```java
// ✅ 正确：MyBatis-Plus 自动带上 WHERE lock_version = ?
order.setOrderState(OrderState.PAID);
int rows = orderMapper.updateById(order);
if (rows == 0) {
    throw new OrderException(OrderErrorCode.ORDER_CONCURRENT_CONFLICT);
}
```

规则：
- 更新失败（影响行数 0）必须显式处理，不允许静默忽略
- 高并发扣减类场景（库存、余额）优先用「条件更新」而非乐观锁重试：
  `UPDATE t SET stock = stock - #{n} WHERE id = #{id} AND stock >= #{n}`

### 2.3 逻辑删除

- 统一使用 `deleted` 字段 + `@TableLogic`，禁止物理删除业务数据
- 查询时 MyBatis-Plus 自动追加 `deleted = 0`，**禁止手写该条件**
- 唯一索引必须将 `deleted` 纳入，否则删除后无法重新创建相同业务键

---

## 3. 查询规范

### 3.1 禁止 N+1 查询（红线 R6）

```java
// ❌ 错误：循环里查库，100 个订单就是 100 次查询
for (Order order : orders) {
    List<OrderItem> items = itemMapper.selectByOrderId(order.getOrderId());
    voList.add(convert(order, items));
}

// ✅ 正确：先聚合 id，一次批量查询，再内存分组
List<String> orderIds = orders.stream().map(Order::getOrderId).toList();
List<OrderItem> allItems = itemMapper.selectByOrderIds(orderIds);
Map<String, List<OrderItem>> itemMap = allItems.stream()
        .collect(Collectors.groupingBy(OrderItem::getOrderId));
orders.forEach(order -> voList.add(convert(order,
        itemMap.getOrDefault(order.getOrderId(), Collections.emptyList()))));
```

### 3.2 分页

- 分页参数统一封装为 `PageRequest`（`pageNum` / `pageSize`），出参统一为 `PageResult<T>`
- 必须限制 `pageSize` 上限（建议 100），防止大页拖垮数据库
- 深分页（pageNum 很大）必须改用游标分页（基于上次最大 id）
- 分页查询必须显式指定排序字段，禁止依赖数据库默认顺序

### 3.3 其他

- **禁止 `SELECT *`**，必须显式列出需要的字段
- 单表查询优先用 MyBatis-Plus `LambdaQueryWrapper`（编译期检查字段名），避免字符串硬编码列名
- 批量插入/更新使用 `saveBatch` / `updateBatchById`，不要循环单条
- 大批量数据处理必须分页或分片，禁止一次性加载全表到内存

---

## 4. 事务规范

```java
// ✅ 正确：事务在 Application 层，显式 rollbackFor
@Transactional(rollbackFor = Exception.class)
public OrderCreateResponse create(OrderCreateRequest request) {
    ...
}

// ❌ 错误：默认只对 RuntimeException 回滚，受检异常不回滚
@Transactional
public void create(OrderCreateRequest request) { ... }

// ❌ 错误：只读方法加事务，白白占用连接
@Transactional
public OrderVO detail(String orderId) { ... }
```

规则：
- 事务边界放在 **Application 层**，不在 Controller，也不在 Domain
- 必须显式 `rollbackFor = Exception.class`
- 只读查询方法不加事务
- 事务方法内**禁止**执行远程调用、发消息、写缓存等不可回滚操作（如必须，放到事务提交后）
- 事务方法要短小，禁止在事务内做耗时操作

---

## 5. 缓存与一致性（通用原则）

本册不绑定具体缓存中间件，只约定原则：

- 缓存读取采用 **Cache Aside**：先读缓存，未命中读库并回写
- 更新顺序：**先更新数据库，再删除缓存**（不要更新缓存）
- 缓存 key 必须带业务前缀与版本，格式 `{module}:{biz}:{id}`
- 必须设置过期时间，禁止无过期时间的永驻 key
- 缓存与 DB 一致性无法强保证时，必须有兜底对账或旁路校验机制
