/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.service.local;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LocalAuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public LocalAuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
