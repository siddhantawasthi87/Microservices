package com.conceptandcoding.apigateway.customRouteFilter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CustomRouteGatewayFilterFactory extends AbstractGatewayFilterFactory<CustomRouteGatewayFilterFactory.CustomConfig> {

    public CustomRouteGatewayFilterFactory() {
        super(CustomConfig.class);
    }

    @Override
    public GatewayFilter apply(CustomConfig config) {
        return (exchange, chain) -> {

            System.out.println("pre filter logic here, config value: " + config.getCountry());

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                System.out.println("post filter logic here");
            }));
        };
    }

    public static class CustomConfig {
        private String country;

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }

}
