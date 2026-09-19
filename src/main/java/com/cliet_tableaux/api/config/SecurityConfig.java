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
                // Sans ceci, une requête sans JWT reçoit un principal "anonyme" par défaut, que
                // hasRole()/hasAuthority() traitent comme "authentifié mais insuffisant" -> 403,
                // même en l'absence totale de token. En le désactivant, l'absence de JWT laisse
                // l'authentification à null, ce qui produit le 401 attendu (AuthenticationException
                // via AuthorizationFilter) ; un JWT valide mais avec un rôle insuffisant continue de
                // donner 403 (AccessDeniedException). Les routes permitAll() ne sont pas affectées :
                // elles n'exigent aucune authentification, avec ou sans ce filtre.
                .anonymous(AbstractHttpConfigurer::disable)
                // Sans AuthenticationEntryPoint explicite, Spring Security utilise par défaut
                // Http403ForbiddenEntryPoint, qui renvoie 403 pour TOUT échec d'authentification,
                // y compris une absence totale de JWT — masquant la distinction 401 (pas authentifié)
                // / 403 (authentifié, rôle insuffisant). On force ici le 401 standard REST.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                                // Public : Stripe ne peut pas fournir de JWT, la signature du payload
                                // (vérifiée dans WebhookService via le secret Stripe) fait office d'authentification.
                                .requestMatchers(HttpMethod.POST, "/api/v1/orders/stripe-webhooks").permitAll()
                                // Route de commande : le userId est déduit du principal authentifié, jamais du body.
                                .requestMatchers("/api/v1/orders/**").authenticated()
                                // Catalogue : consultation publique, écriture réservée aux admins
                                // (AUDIT_BACKEND.md, finding #4 — nécessite le préfixe ROLE_ ajouté sur
                                // User.getAuthorities(), finding #9, sans quoi hasRole("ADMIN") échouerait
                                // silencieusement pour tout le monde, admin compris).
                                .requestMatchers(HttpMethod.GET, "/api/v1/paintings/**").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/paintings").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/v1/paintings/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/paintings/**").hasRole("ADMIN")
                                // Liste des utilisateurs : données sensibles (emails, hash bcrypt), jamais publique.
                                .requestMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")
                                // Signature d'upload Cloudinary : réservée à la gestion du catalogue (admin).
                                .requestMatchers(HttpMethod.POST, "/api/v1/cloudinary-signature").hasRole("ADMIN")
                                // Public par design : authentification (sinon personne ne peut se connecter),
                                // formulaires de contact et de demande sur-mesure.
                                .requestMatchers("/api/v1/auth/**", "/api/v1/contact", "/api/v1/custom-requests").permitAll()
                                .anyRequest().permitAll()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

