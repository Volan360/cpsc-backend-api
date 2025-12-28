package com.cpsc.backend.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private final JwtValidator jwtValidator;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        logger.debug("JWT Filter processing: {} {}", request.getMethod(), requestPath);
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            logger.debug("Found Authorization header for path: {}", requestPath);
            
            // Validate and verify the JWT token against Cognito's public keys
            DecodedJWT decodedJWT = jwtValidator.validateToken(token);
            
            if (decodedJWT != null) {
                String userId = jwtValidator.getUserId(decodedJWT);
                
                if (userId != null) {
                    // Create authentication with the verified user ID
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(userId, token, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    logger.debug("Authenticated user: {}", userId);
                } else {
                    logger.warn("JWT token missing user ID (sub claim)");
                }
            } else {
                logger.warn("JWT token validation failed for request: {} {}", 
                    request.getMethod(), request.getRequestURI());
            }
        } else {
            logger.debug("No Authorization header found for path: {}", requestPath);
        }
        
        filterChain.doFilter(request, response);
    }
}
