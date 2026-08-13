/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.studio.agent.common.constant.Constants;
import com.openjiuwen.studio.agent.common.constant.SimpleConstants;
import com.openjiuwen.studio.agent.common.dto.simple.SimpleUser;
import com.openjiuwen.studio.agent.common.utils.RequestContextUtils;
import com.openjiuwen.studio.agent.manager.dto.auth.LocalAuthErrorResponse;
import com.openjiuwen.studio.agent.manager.entity.User;
import com.openjiuwen.studio.agent.manager.service.local.LocalAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public class LocalSessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String SESSION_COOKIE_NAME = "AUTH_SESSION";
    private static final String CURRENT_USER = "CURRENT_USER";
    private static final String REMOTE_VALIDATE_PATH = "/v3/auth/tokens";

    private final LocalAccountService accountService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalSessionAuthenticationFilter(LocalAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (isPublicRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sessionId = getSessionId(request);
        Optional<User> user = accountService.findUserBySession(sessionId);
        if (user.isEmpty()) {
            writeUnauthorized(response);
            return;
        }

        SimpleUser simpleUser = accountService.toSimpleUser(user.get(), sessionId);
        request.setAttribute(CURRENT_USER, simpleUser);
        request.setAttribute(SimpleConstants.AGENT_SID, sessionId);
        RequestContextUtils.setContext(simpleUser);
        HttpServletRequest wrappedRequest = withAuthToken(request, sessionId);
        try {
            accountService.refreshSession(sessionId);
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            RequestContextUtils.remove();
        }
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || path.startsWith("/auth/local/")
            || path.equals("/auth/local")
            || path.equals(REMOTE_VALIDATE_PATH)
            || path.equals("/health")
            || path.equals("/v1/health")
            || path.equals("/v1/agent-manager/health")
            || path.startsWith("/actuator/")
            || path.equals("/error")
            || path.equals("/favicon.ico");
    }

    private HttpServletRequest withAuthToken(HttpServletRequest request, String sessionId) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                String currentValue = super.getHeader(name);
                if (Constants.Header.X_AUTH_TOKEN.equalsIgnoreCase(name) && StringUtils.isBlank(currentValue)) {
                    return sessionId;
                }
                return currentValue;
            }
        };
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
            new LocalAuthErrorResponse("AUTH_REQUIRED", "登录已过期，请重新登录"));
    }

    public static String getSessionId(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        Optional<Cookie> authSession = Arrays.stream(request.getCookies())
            .filter(cookie -> SESSION_COOKIE_NAME.equals(cookie.getName()))
            .findFirst();
        return authSession.or(() -> Arrays.stream(request.getCookies())
                .filter(cookie -> SimpleConstants.AGENT_SID.equals(cookie.getName()))
                .findFirst())
            .map(Cookie::getValue)
            .orElse(null);
    }
}
