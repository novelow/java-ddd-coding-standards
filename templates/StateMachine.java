package com.yourcompany.common.base.statemachine;

/**
 * 状态机抽象。
 *
 * <p>任何"状态 + 事件 → 新状态"的流转必须走状态机，
 * 禁止在业务代码里用 if-else 直接给状态字段赋值。
 */
public interface StateMachine<STATE, EVENT> {

    /**
     * 执行状态流转
     *
     * @param state 当前状态
     * @param event 触发事件
     * @return 目标状态
     * @throws com.yourcompany.common.base.exception.BizException 流转未注册时抛出
     */
    STATE transition(STATE state, EVENT event);
}
