/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.controller;

import com.openjiuwen.studio.agent.manager.dto.auth.LocalAuthErrorResponse;
import com.openjiuwen.studio.agent.manager.service.local.LocalAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LocalAuthController.class)
public class LocalAuthExceptionHandler {
    @ExceptionHandler(LocalAuthException.class)
    public ResponseEntity<LocalAuthErrorResponse> handleLocalAuthException(LocalAuthException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(new LocalAuthErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<LocalAuthErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
            ? "请求参数不正确"
            : exception.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new LocalAuthErrorResponse("VALIDATION_FAILED", message));
    }
}
