/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.service.local;

import com.openjiuwen.studio.agent.manager.dto.auth.LocalRegisterRequest;
import com.openjiuwen.studio.agent.manager.entity.Session;
import com.openjiuwen.studio.agent.manager.entity.User;
import com.openjiuwen.studio.agent.manager.repository.UserRepository;
import com.openjiuwen.studio.agent.manager.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalAccountServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private LocalAccountService accountService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountService, "registrationEnabled", true);
        ReflectionTestUtils.setField(accountService, "defaultDomainId", "0");
        ReflectionTestUtils.setField(accountService, "defaultProjectId", "0");
    }

    @Test
    void registerCreatesInternalUserAndSession() {
        LocalRegisterRequest registerRequest = new LocalRegisterRequest();
        registerRequest.setUsername("Alice");
        registerRequest.setPassword("password123");
        registerRequest.setRealName("Alice Zhang");
        registerRequest.setEmail("alice@example.com");
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Session session = new Session();
        session.setSessionId("session-id");
        when(sessionService.createSession(any(User.class), any(HttpServletRequest.class))).thenReturn(session);

        LocalAccountService.AuthenticatedSession result = accountService.register(registerRequest, request);

        assertEquals("alice", result.user().getUsername());
        assertEquals("bcrypt-hash", result.user().getPasswordHash());
        assertEquals(User.UserSource.INTERNAL, result.user().getSource());
        assertEquals("session-id", result.sessionId());
    }

    @Test
    void loginAcceptsActiveInternalUser() {
        User user = activeUser();
        user.setPasswordHash("bcrypt-hash");
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        Session session = new Session();
        session.setSessionId("session-id");
        when(sessionService.createSession(user, request)).thenReturn(session);

        LocalAccountService.AuthenticatedSession result = accountService.login(" Alice ", "password123", request);

        assertEquals(user, result.user());
        assertEquals("session-id", result.sessionId());
    }

    @Test
    void loginRejectsInvalidCredentialsWithoutRevealingCause() {
        when(userRepository.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());

        LocalAuthException exception = assertThrows(LocalAuthException.class,
            () -> accountService.login("missing", "password123", request));

        assertEquals("INVALID_CREDENTIALS", exception.getCode());
    }

    @Test
    void validateTokenReturnsSimpleUserBoundToSession() {
        User user = activeUser();
        when(sessionService.getUserBySession("session-id")).thenReturn(Optional.of(user));

        var result = accountService.validateToken("session-id");

        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUserId());
        assertEquals("session-id", result.get().getToken());
        assertEquals("0", result.get().getProjectId());
        verify(sessionService).refreshSession("session-id");
    }

    @Test
    void logoutInvalidatesServerSession() {
        accountService.logout("session-id");
        verify(sessionService).logoutSession("session-id");
    }

    private User activeUser() {
        return User.builder()
            .username("alice")
            .realName("Alice")
            .domainId("0")
            .projectId("0")
            .source(User.UserSource.INTERNAL)
            .isActive(true)
            .build();
    }
}
