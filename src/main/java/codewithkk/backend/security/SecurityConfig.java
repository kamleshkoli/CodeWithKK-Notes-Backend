package codewithkk.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // --- public ---
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/notes", "/api/notes/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payment/create-order").permitAll()

                        // --- admin only: catalogue writes, uploads, admin console ---
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Minting a ticket is a buyer action, not a catalogue write, so
                        // it must be listed before the blanket POST rule below -
                        // otherwise /api/notes/{id}/download-ticket would be treated
                        // as a note creation and demand ROLE_ADMIN.
                        .requestMatchers(HttpMethod.POST, "/api/notes/*/download-ticket")
                        .authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/notes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/notes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/notes/**").hasRole("ADMIN")
                        .requestMatchers("/api/upload/**").hasRole("ADMIN")
                        .requestMatchers("/api/files/**").hasRole("ADMIN")

                        // --- signed-in users ---
                        // NOTE: /api/notes/*/download is deliberately NOT listed here.
                        //
                        // It is reached by a plain browser navigation (a tap), which
                        // cannot attach the Authorization header that lives in
                        // localStorage. Requiring authentication at the filter layer
                        // would reject every legitimate mobile download with a 403
                        // before the controller ran.
                        //
                        // Authorization is NOT skipped - it moved into
                        // NoteController.downloadNote, which accepts either
                        //   (a) a valid JWT + completed purchase, or
                        //   (b) a single-use, 2-minute ticket that was itself only
                        //       mintable after a completed-purchase check, and which
                        //       re-verifies the purchase before streaming a byte.
                        // Anything else returns 401/403 and no PDF is written.
                        .requestMatchers("/api/payment/**").authenticated()
                        .requestMatchers("/api/bundle/**").authenticated()
                        .requestMatchers("/api/user/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
