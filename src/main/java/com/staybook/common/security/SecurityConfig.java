package com.staybook.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 토큰 없이 접근하는 조회(GET) 경로: 숙소·리뷰 조회와 그에 딸린 목록, 업로드된 이미지. */
    private static final String[] PUBLIC_GET = {
            "/api/terms",
            "/api/accommodations/**",
            "/api/regions",
            "/api/amenities",
            "/api/categories",
            "/uploads/**",
            //11/12 스웨거 의존성 추가
            "/swagger-ui/**",       // Swagger UI 관련 정적 리소스(HTML, JS, CSS) 전체
            "/swagger-ui.html",     // 구버전/리다이렉트 주소 호환
            "/v3/api-docs/**",       // OpenAPI 스펙 JSON 데이터 경로 (가장 중요!)

    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
                                                   SecurityErrorResponder errorResponder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorResponder)
                        .accessDeniedHandler(errorResponder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/host/**").hasRole(UserRole.HOST.name())
                        .requestMatchers("/api/admin/**").hasRole(UserRole.ADMIN.name())
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
