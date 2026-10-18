package com.yourcompany.common.base.statemachine;

import com.yourcompany.common.base.exception.BizErrorCode;
import com.yourcompany.common.base.exception.BizException;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态机基础实现。
 *
 * <p>子类在实例初始化块中注册所有合法流转，并以单例 INSTANCE 暴露：
 *
 * <pre>{@code
 * public class OrderStateMachine extends BaseStateMachine<OrderState, OrderEvent> {
 *     public static final OrderStateMachine INSTANCE = new OrderStateMachine();
 *     {
 *         putTransition(OrderState.CREATE, OrderEvent.CONFIRM, OrderState.CONFIRM);
 *         putTransition(OrderState.CONFIRM, OrderEvent.PAY, OrderState.PAID);
 *     }
 * }
 * }</pre>
 *
 * <p>无状态、线程安全。未注册的流转抛异常，不允许静默返回原状态。
 */
public class BaseStateMachine<STATE, EVENT> implements StateMachine<STATE, EVENT> {

    private final Map<String, STATE> transitions = new HashMap<>();

    protected void putTransition(STATE origin, EVENT event, STATE target) {
        transitions.put(key(origin, event), target);
    }

    @Override
    public STATE transition(STATE state, EVENT event) {
        STATE target = transitions.get(key(state, event));
        if (target == null) {
            throw new BizException(BizErrorCode.STATE_TRANSITION_FAILED,
                    "state=" + state + ", event=" + event);
        }
        return target;
    }

    private String key(STATE state, EVENT event) {
        return state + "_" + event;
    }
}
