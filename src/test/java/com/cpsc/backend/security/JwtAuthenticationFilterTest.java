package com.cpsc.backend.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtValidator jwtValidator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private DecodedJWT decodedJWT;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtValidator);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_ValidToken_SetsAuthentication() throws ServletException, IOException {
        String token = "valid.jwt.token";
        String userId = "user-123";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getRequestURI()).thenReturn("/api/secure/profile");
        when(request.getMethod()).thenReturn("GET");
        when(jwtValidator.validateToken(token)).thenReturn(decodedJWT);
        when(jwtValidator.getUserId(decodedJWT)).thenReturn(userId);

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(userId);
        assertThat(authentication.getCredentials()).isEqualTo(token);
        assertThat(authentication.getAuthorities()).isEmpty();

        verify(filterChain).doFilter(request, response);
        verify(jwtValidator).validateToken(token);
        verify(jwtValidator).getUserId(decodedJWT);
    }

    @Test
    void doFilterInternal_NoAuthHeader_ContinuesWithoutAuthentication() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/hello");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
        verify(jwtValidator, never()).validateToken(anyString());
    }

    @Test
    void doFilterInternal_InvalidHeaderFormat_ContinuesWithoutAuthentication() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("InvalidFormat token");
        when(request.getRequestURI()).thenReturn("/api/secure/profile");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
        verify(jwtValidator, never()).validateToken(anyString());
    }

    @Test
    void doFilterInternal_InvalidToken_ContinuesWithoutAuthentication() throws ServletException, IOException {
        String token = "invalid.jwt.token";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getRequestURI()).thenReturn("/api/secure/profile");
        when(request.getMethod()).thenReturn("GET");
        when(jwtValidator.validateToken(token)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
        verify(jwtValidator).validateToken(token);
        verify(jwtValidator, never()).getUserId(any());
    }

    @Test
    void doFilterInternal_ValidTokenButNoUserId_ContinuesWithoutAuthentication() throws ServletException, IOException {
        String token = "valid.jwt.token";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getRequestURI()).thenReturn("/api/secure/profile");
        when(request.getMethod()).thenReturn("GET");
        when(jwtValidator.validateToken(token)).thenReturn(decodedJWT);
        when(jwtValidator.getUserId(decodedJWT)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
        verify(jwtValidator).validateToken(token);
        verify(jwtValidator).getUserId(decodedJWT);
    }

    @Test
    void doFilterInternal_EmptyBearerToken_ContinuesWithoutAuthentication() throws ServletException, IOException {
        String emptyToken = "";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + emptyToken);
        when(request.getRequestURI()).thenReturn("/api/secure/profile");
        when(request.getMethod()).thenReturn("GET");
        when(jwtValidator.validateToken(emptyToken)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
        verify(jwtValidator).validateToken(emptyToken);
    }

    @Test
    void doFilterInternal_PublicEndpoint_NoAuthenticationRequired() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/hello");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
        verify(jwtValidator, never()).validateToken(anyString());
    }

    @Test
    void doFilterInternal_MultipleRequests_ClearsContextBetweenRequests() throws ServletException, IOException {
        String token1 = "token1";
        String userId1 = "user-1";
        
        // First request
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token1);
        when(request.getRequestURI()).thenReturn("/api/secure/profile");
        when(request.getMethod()).thenReturn("GET");
        when(jwtValidator.validateToken(token1)).thenReturn(decodedJWT);
        when(jwtValidator.getUserId(decodedJWT)).thenReturn(userId1);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth1 = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth1).isNotNull();
        assertThat(auth1.getName()).isEqualTo(userId1);

        // Clear context for next request
        SecurityContextHolder.clearContext();

        // Second request without auth header
        when(request.getHeader("Authorization")).thenReturn(null);
        filter.doFilterInternal(request, response, filterChain);

        Authentication auth2 = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth2).isNull();
    }
}
