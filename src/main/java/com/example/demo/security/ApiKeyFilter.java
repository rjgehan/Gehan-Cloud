package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String expected;

    public ApiKeyFilter(@Value("${share.key}") String expected) {
        this.expected = expected;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !(req.getRequestURI().startsWith("/api/share") && "POST".equalsIgnoreCase(req.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // Fail closed: if SHARE_KEY was never set, no request can authenticate.
        if (expected == null || expected.isBlank()) {
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        String got = req.getHeader("key");
        if (got == null || !got.equals(expected)) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(req, res);
    }
}
