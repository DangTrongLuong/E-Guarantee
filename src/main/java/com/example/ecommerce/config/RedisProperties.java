package com.example.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Getter
@Setter
public class RedisProperties {

    private String host;
    private int port;
    private String username;
    private String password ;
    private Ssl ssl = new Ssl();

    @Getter
    @Setter
    public static class Ssl {
        private boolean enabled = false;
    }
}