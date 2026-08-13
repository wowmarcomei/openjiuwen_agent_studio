/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.controller;

import com.openjiuwen.studio.agent.common.constant.SimpleConstants;
import com.openjiuwen.studio.agent.manager.dto.auth.LocalLoginRequest;
import com.openjiuwen.studio.agent.manager.dto.auth.LocalRegisterRequest;
import com.openjiuwen.studio.agent.manager.dto.auth.LocalUserResponse;
import com.openjiuwen.studio.agent.manager.entity.User;
import com.openjiuwen.studio.agent.manager.filter.LocalSessionAuthenticationFilter;
import com.openjiuwen.studio.agent.manager.service.local.LocalAccountService;
import com.openjiuwen.studio.agent.manager.service.local.LocalAuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth/local")
@RequiredArgsConstructor
@ConditionalOnExpression("'simple'.equals('${env.type:}') && ''.equals('${auth.sso.validate-url:}')"
    + " && 'true'.equalsIgnoreCase('${local.auth.enabled:true}') && 'false'.equalsIgnoreCase('${saml.enabled:false}')")
public class LocalAuthController {
    private final LocalAccountService accountService;

    @Value("${app.auth.session.absolute-timeout:86400}")
    private long sessionMaxAgeSeconds;

    @Value("${local.auth.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${local.auth.cookie-same-site:Lax}")
    private String cookieSameSite;

    @PostMapping("/register")
    public ResponseEntity<LocalUserResponse> register(@Valid @RequestBody LocalRegisterRequest request,
        HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        LocalAccountService.AuthenticatedSession session = accountService.register(request, servletRequest);
        setSessionCookies(servletResponse, session.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(LocalUserResponse.from(session.user()));
    }

    @PostMapping("/login")
    public LocalUserResponse login(@Valid @RequestBody LocalLoginRequest request, HttpServletRequest servletRequest,
        HttpServletResponse servletResponse) {
        LocalAccountService.AuthenticatedSession session = accountService.login(request.getUsername(),
            request.getPassword(), servletRequest);
        setSessionCookies(servletResponse, session.sessionId());
        return LocalUserResponse.from(session.user());
    }

    @GetMapping("/me")
    public LocalUserResponse me(HttpServletRequest request) {
        String sessionId = LocalSessionAuthenticationFilter.getSessionId(request);
        User user = accountService.findUserBySession(sessionId)
            .orElseThrow(() -> new LocalAuthException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录"));
        accountService.refreshSession(sessionId);
        return LocalUserResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        accountService.logout(LocalSessionAuthenticationFilter.getSessionId(request));
        clearSessionCookies(response);
        return ResponseEntity.noContent().build();
    }

    private void setSessionCookies(HttpServletResponse response, String sessionId) {
        addCookie(response, LocalSessionAuthenticationFilter.SESSION_COOKIE_NAME, sessionId,
            Duration.ofSeconds(sessionMaxAgeSeconds));
        addCookie(response, SimpleConstants.AGENT_SID, sessionId, Duration.ofSeconds(sessionMaxAgeSeconds));
    }

    private void clearSessionCookies(HttpServletResponse response) {
        addCookie(response, LocalSessionAuthenticationFilter.SESSION_COOKIE_NAME, "", Duration.ZERO);
        addCookie(response, SimpleConstants.AGENT_SID, "", Duration.ZERO);
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(maxAge)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
