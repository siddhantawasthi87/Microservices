package com.conceptandcoding.apigateway.globalFilter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String uriPath = exchange.getRequest().getURI().getPath();

        //Skip JWT validation for auth endpoints
        if (uriPath.startsWith("/auth")) {
            return chain.filter(exchange);
        }

        //Skip /products and /orders endpoint also for testing purpose only
        if (uriPath.startsWith("/products") || uriPath.startsWith("/orders")) {
            return chain.filter(exchange);
        }

        //Extract Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) {
            //if token is missing
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String jwtToken = authHeader.substring(7);
        try {
            //verify JWT token logic here....
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // call next filter chain and also add post-filter logic
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            //post filter logic here
        }));
    }

    @Override
    public int getOrder() {
        // lower value means higher priority
        return -1;
    }
}
