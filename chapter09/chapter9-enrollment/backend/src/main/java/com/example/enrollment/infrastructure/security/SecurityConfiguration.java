package com.example.enrollment.infrastructure.security;

import com.example.enrollment.interfaces.http.SecurityJson;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        String encoded = encoder.encode("Chapter9!Demo");
        Map<String, CampusPrincipal> users =
                Map.of(
                        "student101", new CampusPrincipal("student101", encoded, "STUDENT", 101L),
                        "student102", new CampusPrincipal("student102", encoded, "STUDENT", 102L),
                        "teacher201", new CampusPrincipal("teacher201", encoded, "TEACHER", 201L));
        return username -> {
            var user = users.get(username);
            if (user == null) throw new UsernameNotFoundException("Unknown account");
            String role = user.getAuthorities().iterator().next().getAuthority().substring(5);
            return new CampusPrincipal(user.getUsername(), encoded, role, user.getStudentId());
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityJson json) throws Exception {
        // Teaching client adds Authorization explicitly and omits browser cookies.
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(
                auth -> auth.requestMatchers("/error").permitAll().anyRequest().authenticated());
        http.httpBasic(
                basic ->
                        basic.authenticationEntryPoint(
                                (req, res, ex) ->
                                        json.error(req, res, 401, "UNAUTHENTICATED", "请先登录")));
        http.exceptionHandling(
                errors ->
                        errors.authenticationEntryPoint(
                                        (req, res, ex) ->
                                                json.error(
                                                        req, res, 401, "UNAUTHENTICATED", "请先登录"))
                                .accessDeniedHandler(
                                        (req, res, ex) ->
                                                json.error(
                                                        req,
                                                        res,
                                                        403,
                                                        "FORBIDDEN",
                                                        "当前身份没有该操作权限")));
        http.requestCache(cache -> cache.disable());
        return http.build();
    }
}
