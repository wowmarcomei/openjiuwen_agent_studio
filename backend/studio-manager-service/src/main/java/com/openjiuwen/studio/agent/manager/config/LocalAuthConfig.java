/* Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.studio.agent.manager.config;

import com.openjiuwen.studio.agent.manager.filter.LocalSessionAuthenticationFilter;
import com.openjiuwen.studio.agent.manager.service.local.LocalAccountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnExpression("'simple'.equals('${env.type:}') && ''.equals('${auth.sso.validate-url:}')"
    + " && 'true'.equalsIgnoreCase('${local.auth.enabled:true}') && 'false'.equalsIgnoreCase('${saml.enabled:false}')")
public class LocalAuthConfig {
    @Bean
    public PasswordEncoder localPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<LocalSessionAuthenticationFilter> localSessionAuthenticationFilter(
        LocalAccountService accountService) {
        FilterRegistrationBean<LocalSessionAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LocalSessionAuthenticationFilter(accountService));
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    @Bean
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
