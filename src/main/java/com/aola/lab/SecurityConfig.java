package com.aola.lab;

import com.aola.lab.Model.LoginLog;
import com.aola.lab.Model.User;
import com.aola.lab.Repositories.Logs;
import com.aola.lab.Repositories.Users;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

@Configuration
public class SecurityConfig {
    static final String ADMIN_USERNAME = "aolaAdmin";
    static final String ADMIN_PASSWORD = "aolaResource";
    static final String ADMIN_VERIFICATION_CODE = "5aWl5ouJ5pif5oCA5pen6LWE5rqQ5pW05ZCI";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> authenticationDetailsSource() {
        return request -> new LoginAuthenticationDetails(request);
    }

    @Bean
    AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        return new LoginAuthenticationProvider(userDetailsService, passwordEncoder);
    }

    @Bean
    UserDetailsService userDetailsService(Users users) {
        return name -> users.findByUsername(name)
                .map(user -> new AccountDetails(user))
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Users users, Logs logs,
            AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> detailsSource,
            AuthenticationProvider loginProvider) throws Exception {
        return http.authenticationProvider(loginProvider)
                .addFilterBefore(new org.springframework.web.filter.OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                            jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, java.io.IOException {
                        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                        if (auth != null && auth.getPrincipal() instanceof AccountDetails details) {
                            User current = users.findById(details.id).orElse(null);
                            if (current == null || !current.getUsername().equals(details.getUsername())
                                    || current.getSecurityVersion() != details.securityVersion) {
                                new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                                        .logout(request, response, auth);
                            }
                        }
                        chain.doFilter(request, response);
                    }
                }, org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/images/**", "/login", "/register", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").authenticationDetailsSource(detailsSource)
                .successHandler((request, response, auth) -> {
                    User user = users.findByUsername(auth.getName()).orElseThrow();
                    LoginLog log = new LoginLog();
                    log.setUser(user);
                    logs.save(log);
                    request.getSession(true).setAttribute("showLoginWelcome", true);
                    response.sendRedirect(request.getContextPath() + "/");
                }).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }

    @Bean
    ApplicationRunner bootstrapAdmin(Users users, PasswordEncoder encoder) {
        return args -> {
            User existing = users.findByUsername(ADMIN_USERNAME).orElse(null);
            if (existing != null) {
                if (!existing.isAdmin()) {
                    throw new IllegalStateException("内置管理员用户名 " + ADMIN_USERNAME + " 已被普通用户占用");
                }
                return;
            }
            User admin = new User();
            admin.setUsername(ADMIN_USERNAME);
            admin.setPassword(encoder.encode(ADMIN_PASSWORD));
            admin.setAdmin(true);
            users.save(admin);
        };
    }

    static final class AccountDetails extends org.springframework.security.core.userdetails.User {
        private final Long id;
        private final long securityVersion;

        AccountDetails(User user) {
            super(user.getUsername(), user.getPassword(), java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")));
            this.id = user.getId();
            this.securityVersion = user.getSecurityVersion();
        }
    }

    static final class LoginAuthenticationDetails extends WebAuthenticationDetails {
        private final String verificationCode;
        private final String role;

        private LoginAuthenticationDetails(HttpServletRequest request) {
            super(request);
            this.verificationCode = request.getParameter("verificationCode");
            this.role = request.getParameter("role");
        }

        String verificationCode() {
            return verificationCode;
        }

        String role() {
            return role;
        }
    }

    record LoginAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder)
            implements AuthenticationProvider {
        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            String name = authentication.getName();
            String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
            String code = authentication.getDetails() instanceof LoginAuthenticationDetails details
                    ? details.verificationCode() : null;
            String role = authentication.getDetails() instanceof LoginAuthenticationDetails details
                    ? details.role() : null;
            UserDetails user = userDetailsService.loadUserByUsername(name);
            boolean isAdmin = user.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if ("ADMIN".equals(role) && !isAdmin) {
                throw new BadCredentialsException("该账号不是管理员");
            }
            if ("PERSONAL".equals(role) && isAdmin) {
                throw new BadCredentialsException("管理员账号请选择管理员身份");
            }
            if (isAdmin && !ADMIN_VERIFICATION_CODE.equals(code)) {
                throw new BadCredentialsException("管理员验证码错误");
            }
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new BadCredentialsException("用户名或密码错误");
            }
            return UsernamePasswordAuthenticationToken.authenticated(user, password, user.getAuthorities());
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }
}
