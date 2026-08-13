/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.dto.auth;

import com.openjiuwen.studio.agent.manager.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalUserResponse {
    private String userId;
    private String username;
    private String realName;
    private String email;
    private String domainId;
    private String projectId;

    public static LocalUserResponse from(User user) {
        return LocalUserResponse.builder()
            .userId(user.getUsername())
            .username(user.getUsername())
            .realName(user.getRealName())
            .email(user.getEmail())
            .domainId(user.getDomainId())
            .projectId(user.getProjectId())
            .build();
    }
}
