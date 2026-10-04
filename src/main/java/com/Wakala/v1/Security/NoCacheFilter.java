package com.Wakala.v1.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class NoCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Kwa kurasa za HTML (sio static resources, sio API)
        if (!path.startsWith("/css/")
                && !path.startsWith("/js/")
                && !path.startsWith("/images/")
                && !path.startsWith("/favicon")
                && !path.startsWith("/api/")) {

            response.setHeader("Cache-Control",
                    "no-store, no-cache, must-revalidate, max-age=0, private");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            response.setDateHeader("Last-Modified", System.currentTimeMillis());
        }

        filterChain.doFilter(request, response);
    }
}