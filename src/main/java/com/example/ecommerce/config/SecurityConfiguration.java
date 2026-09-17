package com.example.ecommerce.config;

import com.example.ecommerce.service.UserDetailServiceCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JWTDecoderConfiguration jwtDecoderConfiguration;
    private final UserDetailServiceCustom userDetailServiceCustom;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null || roles.isEmpty()) {
                return List.of();
            }
            return roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());
        });


        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register").permitAll()
                        .requestMatchers("/auth/logout").hasAnyRole("MARKER", "CHECKER")
                        .requestMatchers(HttpMethod.GET, "/users").hasAnyRole("MARKER", "CHECKER")
                        .requestMatchers("/customers").hasAnyRole("MARKER", "CHECKER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/guarantees").hasRole("MARKER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/guarantees/**").hasRole("MARKKER")
                        .requestMatchers(HttpMethod.POST, "api/v1/guarantees/*/submit").hasRole("MARKER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/guarantees/*/approve").hasRole("CHECKER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/guarantees/*/reject").hasRole("CHECKER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/guarantees/**").hasAnyRole("MARKER", "CHECKER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer((oauth2) -> oauth2
                        .jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoderConfiguration).jwtAuthenticationConverter(jwtAuthConverter))
                );
        return http.build();

    }
    @Bean
    public AuthenticationManager authenticationManager(){
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailServiceCustom);
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(authenticationProvider);
    }

    private PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
