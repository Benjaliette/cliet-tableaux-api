package com.cliet_tableaux.api.config;

import com.cliet_tableaux.api.core.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configurations w filterChains, cors, authorization, authentication etc ...
 * @author benjadev
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers(HttpMethod.POST, "/api/v1/orders/stripe-webhooks").permitAll()
                                .requestMatchers("/api/v1/orders/**").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/v1/paintings/**").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/paintings").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/v1/paintings/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/paintings/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/v1/cloudinary-signature").hasRole("ADMIN")
                                .requestMatchers("/api/v1/auth/**", "/api/v1/contact", "/api/v1/custom-requests").permitAll()
                                .anyRequest().permitAll()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

