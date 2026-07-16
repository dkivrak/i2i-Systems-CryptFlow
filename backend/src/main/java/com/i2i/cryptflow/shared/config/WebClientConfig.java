package com.i2i.cryptflow.shared.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    /**
     * Customizes the Spring-managed WebClient.Builder to use the JDK's native blocking DNS resolver
     * instead of Netty's asynchronous resolver. This resolves DNS SERVFAIL/UnknownHostException
     * issues frequently encountered in Docker/macOS/VPN environments when calling external APIs.
     */
    @Bean
    public WebClientCustomizer webClientCustomizer() {
        return builder -> builder.clientConnector(
            new ReactorClientHttpConnector(
                HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE)
            )
        );
    }
}
