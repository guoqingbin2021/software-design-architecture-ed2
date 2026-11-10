package edu.textbook.selection.security;

import edu.textbook.selection.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, HttpErrorWriter errors)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(
                        basic ->
                                basic.authenticationEntryPoint(
                                        (request, response, exception) -> {
                                            response.setHeader(
                                                    "WWW-Authenticate",
                                                    "Basic realm=\"course-selection\"");
                                            errors.write(
                                                    request,
                                                    response,
                                                    401,
                                                    "UNAUTHENTICATED",
                                                    "请使用教学账户登录");
                                        }))
                .exceptionHandling(
                        handler ->
                                handler.accessDeniedHandler(
                                        (request, response, exception) ->
                                                errors.write(
                                                        request,
                                                        response,
                                                        403,
                                                        "FORBIDDEN",
                                                        "无权执行此操作")))
                .addFilterBefore(new RequestIdFilter(), BasicAuthenticationFilter.class)
                .build();
    }
}
