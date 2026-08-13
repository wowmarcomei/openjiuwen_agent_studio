/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.service.local;

import com.openjiuwen.studio.agent.common.dto.simple.SimpleUser;
import com.openjiuwen.studio.agent.manager.dto.auth.LocalRegisterRequest;
import com.openjiuwen.studio.agent.manager.entity.Session;
import com.openjiuwen.studio.agent.manager.entity.User;
import com.openjiuwen.studio.agent.manager.repository.UserRepository;
import com.openjiuwen.studio.agent.manager.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'simple'.equals('${env.type:}') && ''.equals('${auth.sso.validate-url:}')"
    + " && 'true'.equalsIgnoreCase('${local.auth.enabled:true}') && 'false'.equalsIgnoreCase('${saml.enabled:false}')")
public class LocalAccountService {
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    @Value("${local.auth.registration-enabled:true}")
    private boolean registrationEnabled;

    @Value("${local.auth.default-domain-id:0}")
    private String defaultDomainId;

    @Value("${local.auth.default-project-id:0}")
    private String defaultProjectId;

    @Transactional
    public AuthenticatedSession register(LocalRegisterRequest request, HttpServletRequest servletRequest) {
        if (!registrationEnabled) {
            throw new LocalAuthException(HttpStatus.FORBIDDEN, "REGISTRATION_DISABLED", "当前环境未开放用户注册");
        }
        String username = normalizeUsername(request.getUsername());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new LocalAuthException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "该用户名已被注册");
        }
        validatePasswordBytes(request.getPassword());
        User user = User.builder()
            .username(username)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .realName(request.getRealName().trim())
            .email(normalizeNullable(request.getEmail()))
            .source(User.UserSource.INTERNAL)
            .domainId(defaultDomainId)
            .projectId(defaultProjectId)
            .isActive(true)
            .build();
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new LocalAuthException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "该用户名已被注册");
        }
        Session session = sessionService.createSession(savedUser, servletRequest);
        return new AuthenticatedSession(savedUser, session.getSessionId());
    }

    public AuthenticatedSession login(String rawUsername, String password, HttpServletRequest servletRequest) {
        String username = normalizeUsername(rawUsername);
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new LocalAuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
        User user = userRepository.findByUsernameIgnoreCase(username)
            .filter(this::canLogin)
            .filter(candidate -> candidate.getPasswordHash() != null)
            .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
            .orElseThrow(() -> new LocalAuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "用户名或密码错误"));
        Session session = sessionService.createSession(user, servletRequest);
        return new AuthenticatedSession(user, session.getSessionId());
    }

    public Optional<User> findUserBySession(String sessionId) {
        return sessionService.getUserBySession(sessionId).filter(this::canLogin);
    }

    public Optional<SimpleUser> validateToken(String sessionId) {
        Optional<User> user = findUserBySession(sessionId);
        user.ifPresent(ignored -> sessionService.refreshSession(sessionId));
        return user.map(value -> toSimpleUser(value, sessionId));
    }

    public void refreshSession(String sessionId) {
        sessionService.refreshSession(sessionId);
    }

    public void logout(String sessionId) {
        sessionService.logoutSession(sessionId);
    }

    public SimpleUser toSimpleUser(User user, String sessionId) {
        return SimpleUser.builder()
            .userId(user.getUsername())
            .userName(user.getRealName() == null ? user.getUsername() : user.getRealName())
            .token(sessionId)
            .domainId(user.getDomainId())
            .domainName(user.getDomainId())
            .projectId(user.getProjectId())
            .build();
    }

    private boolean canLogin(User user) {
        return Boolean.TRUE.equals(user.getIsActive())
            && (user.getExpireTime() == null || user.getExpireTime().isAfter(LocalDateTime.now()));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new LocalAuthException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "密码的UTF-8长度不能超过72字节");
        }
    }

    public record AuthenticatedSession(User user, String sessionId) {
    }
}
