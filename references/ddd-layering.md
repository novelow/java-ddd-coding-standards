# DDD 分层与包结构规范

## 1. 标准包结构

```
com.yourcompany.{module}/
├── controller/                  # HTTP 入口
│   └── XxxController.java
├── application/                 # 应用服务（编排、事务边界）
│   └── XxxApplicationService.java
├── domain/
│   ├── entity/                  # 领域实体（含行为，非贫血模型）
│   │   ├── Xxx.java
│   │   └── convertor/           # Entity ↔ 其他对象的转换器
│   ├── service/                 # 领域服务
│   │   ├── XxxService.java      # 接口
│   │   └── impl/
│   │       └── XxxServiceImpl.java
│   ├── request/                 # 领域入参 XxxRequest
│   ├── response/                # 领域出参 XxxResponse
│   ├── event/                   # 领域事件
│   └── constant/                # 领域枚举（状态、事件、类型）
├── infrastructure/
│   ├── mapper/                  # MyBatis Mapper
│   ├── repo/                    # Repository 实现
│   └── exception/               # 模块私有错误码与异常
│       ├── XxxErrorCode.java
│       └── XxxException.java
├── param/                       # Controller 入参 XxxParam
└── vo/                          # 前端出参 XxxVO
```

规则：
- `domain` 包下**不出现**任何 `org.springframework` 的 import（`@Service`、`@Component`、`@Transactional` 均不允许）
- `param` 与 `vo` 放在模块顶层包下（它们属于接口层，不属于领域层）
- `convertor` 命名统一为 `XxxConvertor`，静态方法 `toVO` / `toEntity` / `toRequest`

---

## 2. 各层职责边界

### 2.1 Controller（接口层）

允许：参数校验、获取上下文（操作人/租户）、装配 Request、调用 Application、转 VO。
禁止：业务判断、事务注解、调用 Mapper、try-catch 兜底。

### 2.2 Application（应用层）

允许：编排多个领域服务调用、开启事务、发送领域事件/消息、参数补充（如填充操作时间）。
禁止：承载核心业务规则（规则属于 Domain）、直接写 SQL、调用其他模块的 Mapper。

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderService orderService;
    private final InventoryService inventoryService;

    @Transactional(rollbackFor = Exception.class)
    public OrderCreateResponse create(OrderCreateRequest request) {
        // 1. 编排：先校验后执行
        // 2. 事务边界在此，不分散到 Domain
        Order order = orderService.create(request);
        inventoryService.freeze(request.toInventoryRequest());
        log.info("order created, orderId={}, buyerId={}", order.getOrderId(), request.getBuyerId());
        return OrderCreateResponse.success(order.getOrderId());
    }
}
```

### 2.3 Domain（领域层）

允许：核心业务规则、状态流转、领域事件发布、调用 Repository 抽象。
禁止：依赖 Spring 容器、依赖具体 DB 实现、返回 `Result` / `Response` 给上层之外的调用方。

**实体必须是充血模型**，禁止贫血模型：

```java
// ❌ 错误：贫血模型，只有 getter/setter，业务规则散落在 Service 里
@Getter
@Setter
public class Order extends BaseEntity {
    private String orderId;
    private OrderState state;
}

// 于是 Service 里出现大量这种代码：
if (order.getState() == OrderState.CONFIRM) {
    order.setState(OrderState.PAID);
}

// ✅ 正确：行为内聚在实体上，状态流转走状态机
@Getter
@Setter
public class Order extends BaseEntity {
    private String orderId;
    private OrderState state;

    public Order pay(PayRequest request) {
        this.state = OrderStateMachine.INSTANCE.transition(this.state, OrderEvent.PAY);
        this.payTime = request.getOperateTime();
        return this;
    }

    public boolean payable() {
        return this.state == OrderState.CONFIRM || this.state == OrderState.CREATE;
    }
}
```

### 2.4 Infrastructure（基础设施层）

允许：Mapper、Repository 实现、外部系统适配、缓存实现。
禁止：包含业务规则判断。

### 2.5 依赖注入（强制）

**生产代码必须使用构造器注入，禁止字段注入（红线 R9）。**

```java
// ❌ 错误：字段注入
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
}

// ✅ 正确：构造器注入（Lombok 生成）
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
}

// ✅ 正确：字段多、需要显式控制时手写构造器
@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}
```

为什么禁止字段注入：

| 问题 | 说明 |
|------|------|
| 依赖可变 | 字段无法声明 `final`，运行时可能被反射或测试框架改掉 |
| 掩盖设计缺陷 | 循环依赖在字段注入下能"正常运行"，构造器注入会启动即失败，强迫你重构 |
| 单元测试变慢 | 字段注入必须起 Spring 容器才能注入，构造器注入可直接 `new OrderService(mockRepo)` |
| 半初始化风险 | 字段注入时 Bean 可能处于未完成初始化状态就被使用 |

注意事项：

- `@RequiredArgsConstructor` **只处理 `final` 字段**，混有非 final 字段时会静默漏掉——需要全部参与注入的字段都声明 `final`
- 出现构造器注入导致的循环依赖报错时，**必须重构拆分**（通常是职责划分有问题），
  禁止用 `@Lazy` 或改回字段注入绕过
- 可选依赖可用 setter 注入或 `ObjectProvider`，不要退回字段注入
- **测试类豁免**：`@SpringBootTest` 测试中用 `@Autowired` 注入被测对象是标准做法，不受 R9 约束

---

## 3. 状态机（强制）

**任何"状态字段 + 事件 → 新状态"的流转，必须走状态机。禁止 if-else 直接赋值。**

### 3.1 基础实现

```java
public class BaseStateMachine<STATE, EVENT> implements StateMachine<STATE, EVENT> {

    private final Map<String, STATE> transitions = new HashMap<>();

    protected void putTransition(STATE origin, EVENT event, STATE target) {
        transitions.put(origin + "_" + event, target);
    }

    @Override
    public STATE transition(STATE state, EVENT event) {
        STATE target = transitions.get(state + "_" + event);
        if (target == null) {
            throw new BizException("state=" + state + ", event=" + event,
                                   BizErrorCode.STATE_TRANSITION_FAILED);
        }
        return target;
    }
}
```

### 3.2 具体状态机

```java
public class OrderStateMachine extends BaseStateMachine<OrderState, OrderEvent> {

    public static final OrderStateMachine INSTANCE = new OrderStateMachine();

    {
        putTransition(OrderState.CREATE, OrderEvent.CONFIRM, OrderState.CONFIRM);
        putTransition(OrderState.CONFIRM, OrderEvent.PAY, OrderState.PAID);
        putTransition(OrderState.CREATE, OrderEvent.CANCEL, OrderState.CLOSED);
        putTransition(OrderState.PAID, OrderEvent.FINISH, OrderState.FINISH);
    }
}
```

规则：
- 状态机用单例 `INSTANCE`，无状态，线程安全
- 未注册的流转**必须抛异常**，不允许静默返回原状态
- 每一条 `putTransition` 都要在注释里说明业务含义（为什么允许这个流转）
- 状态机放在 `domain/entity/statemachine/` 或 `domain/constant/` 下，状态枚举与事件枚举放在 `domain/constant/`

### 3.3 ❌ / ✅ 对照

```java
// ❌ 错误：状态判断散落各处，加一个新状态就要改 N 个地方
if (order.getState() == OrderState.CONFIRM) {
    order.setState(OrderState.PAID);
} else {
    throw new BizException("订单状态错误");
}

// ✅ 正确：流转规则集中在一处，非法流转由状态机统一拦截
order.pay(request);
```

---

## 4. 领域服务与接口

```java
// 接口
public interface OrderService {
    Order create(OrderCreateRequest request);
    Order cancel(OrderCancelRequest request);
}

// 实现
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;   // 依赖抽象
    ...
}
```

规则：
- 领域服务接口与实现分离，实现类放 `impl/` 子包
- 领域服务方法入参统一为 `XxxRequest`，出参为实体或 `XxxResponse`
- 单个领域服务方法不超过 60 行；超过则拆分私有方法或引入策略对象

---

## 5. 依赖方向

```
Controller ──→ Application ──→ Domain ──→ Infrastructure(抽象)
                                  ↑              │
                                  └──────────────┘  (实现 Domain 定义的接口)
```

- 禁止 Domain 依赖 Application 或 Controller
- 禁止 Infrastructure 依赖 Application
- 跨模块调用必须走对方的 Application / 对外接口，**禁止直接引用其他模块的 Mapper**

---

## 6. 设计模式落点

| 场景 | 使用模式 |
|------|---------|
| 同一动作有多种算法（如不同的计价规则） | 策略模式 + 工厂，`XxxStrategyFactory` 按类型分发 |
| 状态流转 | 状态机（见第 3 节） |
| 多个校验规则串行 | 责任链，`BaseXxxValidator` + `nextValidator` |
| 复杂对象构建 | Builder，禁止超过 5 个参数的构造函数 |
| 对象转换 | Convertor 静态工具类，禁止 BeanUtils 反射拷贝 |
