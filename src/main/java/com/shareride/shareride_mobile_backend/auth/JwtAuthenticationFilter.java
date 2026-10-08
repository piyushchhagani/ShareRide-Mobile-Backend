package com.shareride.shareride_mobile_backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        System.out.println(
                "JWT -> " +
                request.getMethod() +
                " " +
                path
        );

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isBlank()) {

            System.out.println(
                    "JWT -> NO AUTHORIZATION HEADER"
            );

            filterChain.doFilter(request, response);
            return;
        }

        if (!authHeader.startsWith("Bearer ")) {

            System.out.println(
                    "JWT -> INVALID AUTHORIZATION FORMAT"
            );

            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();

        if (token.isBlank()) {

            System.out.println(
                    "JWT -> EMPTY TOKEN"
            );

            filterChain.doFilter(request, response);
            return;
        }

        try {

            String email = jwtService.extractEmail(token);

            if (email == null || email.isBlank()) {

                System.out.println(
                        "JWT -> TOKEN HAS NO EMAIL"
                );

                SecurityContextHolder.clearContext();

                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            AuthorityUtils.NO_AUTHORITIES
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

            System.out.println(
                    "JWT -> AUTHENTICATED: " + email
            );

            System.out.println(
                    "JWT -> AUTHENTICATED FLAG: " +
                    authentication.isAuthenticated()
            );

        } catch (Exception e) {

            SecurityContextHolder.clearContext();

            System.out.println(
                    "JWT -> INVALID TOKEN: " +
                    e.getClass().getSimpleName()
            );

            System.out.println(
                    "JWT -> REASON: " +
                    e.getMessage()
            );
        }

        filterChain.doFilter(request, response);
    }
}