package com.yourcompany.common.base.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 动态错误码，用于无法预先定义在枚举中的场景（如透传下游错误码）。
 *
 * <p>优先使用枚举错误码，本类仅作为兜底。
 */
@Getter
@AllArgsConstructor
public class SimpleErrorCode implements ErrorCode {

    private final String code;

    private final String message;
}
