package com.dogukan_tufan.shared_expense_tracking_system.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dogukan_tufan.shared_expense_tracking_system.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

// Runs once per request, before the normal username/password filter. If the request carries a
// valid "Authorization: Bearer <token>" header, it puts the matching user into Spring Security's
// SecurityContext so the rest of the app (controllers, @PreAuthorize, etc.) sees an authenticated
// user -- no session/cookie involved, which is what "stateless JWT auth" means in practice.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        if (jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = jwtService.extractEmail(token);
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // A token issued BEFORE this account existed belongs to someone else: a deleted
                // account whose e-mail was later registered again by a new person. Without this
                // check the old phone's token (still valid for up to 24 h) would log into the
                // new account, because the token only names the e-mail. JWT keeps "issued at"
                // in whole seconds, so the account's creation time is cut to seconds too.
                Instant accountCreated = userRepository.findByEmail(email)
                        .map(user -> user.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
                        .orElse(Instant.MAX);
                if (jwtService.extractIssuedAt(token).isBefore(accountCreated)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (UsernameNotFoundException e) {
                // A valid token whose user no longer exists (e.g. a deleted account: its email was
                // changed). Throwing here would escape the security chain and come back as a 500.
                // Staying anonymous makes the normal entry point answer 401 instead.
            }
        }

        filterChain.doFilter(request, response);
    }
}
