/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LocalLoginRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名最多64个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码最多72个字符")
    private String password;
}
