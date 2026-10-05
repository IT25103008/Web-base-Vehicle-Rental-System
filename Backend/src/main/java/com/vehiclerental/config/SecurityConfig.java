package com.vehiclerental.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

@Configuration
@EnableMethodSecurity     // enables @PreAuthorize on controller methods
public class SecurityConfig {

    // BCrypt hashing for stored passwords
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // Stores the logged-in user in the HTTP session (JSESSIONID cookie),
    // so after one successful login the next requests don't need credentials.
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    // Used by AuthController's JSON login endpoint.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Every signed-in session, per user. Lets a password change sign the user
     * out everywhere else, and lets them do so themselves from the account page.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Tells the registry when a session ends, so it does not fill up with dead ones. */
    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository,
                                           SessionRegistry sessionRegistry,
                                           @Value("${rental.security.basic-auth:false}") boolean basicAuth)
            throws Exception {

        if (basicAuth) {
            // postman profile: credentials on every request, no browser session
            // to protect, so no CSRF token either. Never used by the web app.
            http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, e) ->
                    writeJsonError(response, 401, "Unauthorized", "Invalid email or password")));
        } else {
            // The web app signs in with a session cookie, so every state-changing
            // request must also carry the CSRF token. It is handed to the browser
            // in the XSRF-TOKEN cookie; api.js echoes it back in X-XSRF-TOKEN.
            // A page on another site can send the cookie but cannot read it.
            CookieCsrfTokenRepository tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
            tokens.setCookieCustomizer(c -> c.sameSite("Strict").path("/"));
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(tokens)
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable());
        }

        http
            // Use the CORS rules from WebConfig (needed for browser pre-flight requests).
            .cors(Customizer.withDefaults())

            // Session-based auth, with every session tracked in the registry.
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(event -> writeJsonError(event.getResponse(), 401, "Unauthorized",
                    "You were signed out because your password changed or you signed out elsewhere")))
            .securityContext(sc -> sc.securityContextRepository(securityContextRepository))

            // Unauthenticated / forbidden requests get a JSON body and NO
            // "WWW-Authenticate: Basic" header, so the browser frontend never
            // pops up the native login box.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeJsonError(response, 401, "Unauthorized", "Please log in first"))
                .accessDeniedHandler((request, response, e) -> {
                    if (e instanceof org.springframework.security.web.csrf.CsrfException) {
                        writeJsonError(response, 403, "Forbidden",
                            "Your page is out of date (security token missing). Reload the page and try again.");
                    } else {
                        writeJsonError(response, 403, "Forbidden", "You don't have permission for this action");
                    }
                }))

            // Route-level authorisation rules. First match wins.
            .authorizeHttpRequests(auth -> auth
                // the browser frontend (static files served from /static)
                .requestMatchers(HttpMethod.GET,
                    "/", "/index.html", "/favicon.svg", "/css/**", "/js/**", "/assets/**"
                ).permitAll()

                // public account endpoints
                .requestMatchers(
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/login/2fa",
                    "/api/auth/logout",
                    "/api/auth/me",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/auth/verify-email",
                    "/error"
                ).permitAll()

                // anyone (even unauthenticated) can browse vehicles & branches (read-only)
                .requestMatchers(HttpMethod.GET,
                    "/api/vehicles/search", "/api/vehicles/catalogue",
                    "/api/vehicles/{id}", "/api/vehicles/{id}/image",
                    "/api/vehicles/{id}/images", "/api/vehicles/{id}/images/{imageId}",
                    "/api/vehicles/{id}/busy", "/api/vehicles/{id}/next-free").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/branches", "/api/branches/{id}").permitAll()

                // a signed-in person's own account, whatever their role
                .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()

                // Role checks that must happen before the request body is even
                // read: with only @PreAuthorize, body validation runs first and
                // the wrong role gets a 400 listing the fields instead of a 403.
                .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("CUSTOMER")
                .requestMatchers("/api/users/staff", "/api/users/administrators").hasRole("ADMINISTRATOR")
                .requestMatchers(HttpMethod.POST, "/api/vehicles").hasRole("ADMINISTRATOR")
                .requestMatchers(HttpMethod.PUT, "/api/vehicles/{id}").hasRole("ADMINISTRATOR")

                // admin-only endpoints
                .requestMatchers("/api/branches/**").hasRole("ADMINISTRATOR")
                .requestMatchers("/api/claims/**").hasRole("ADMINISTRATOR")
                .requestMatchers("/api/users/**").hasAnyRole("ADMINISTRATOR", "STAFF")
                .requestMatchers("/api/dashboard/**").hasAnyRole("ADMINISTRATOR", "STAFF")

                // staff or admin
                .requestMatchers("/api/handovers/**").hasAnyRole("STAFF", "ADMINISTRATOR")
                .requestMatchers("/api/maintenance/**").hasAnyRole("STAFF", "ADMINISTRATOR")
                .requestMatchers("/api/insurance-policies/**").hasAnyRole("STAFF", "ADMINISTRATOR")
                .requestMatchers("/api/damage-reports/**").hasAnyRole("STAFF", "ADMINISTRATOR")
                .requestMatchers("/api/payments/**").hasAnyRole("STAFF", "ADMINISTRATOR")
                .requestMatchers("/api/vehicles/**").hasAnyRole("STAFF", "ADMINISTRATOR")

                // everything else (bookings, notifications, favourites, 2FA): any logged-in user
                .anyRequest().authenticated()
            );

        return http.build();
    }

    static void writeJsonError(HttpServletResponse response, int status,
                               String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
            "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }

    /**
     * CSRF for a single-page app (the pattern in the Spring Security reference):
     * the raw token from the cookie is accepted in the header, while the
     * rendered token stays BREACH-protected by the XOR handler.
     */
    static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
                return super.resolveCsrfTokenValue(request, csrfToken);
            }
            return delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /** Makes sure the XSRF-TOKEN cookie is written on every response, even a GET. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
            if (token != null) {
                token.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
