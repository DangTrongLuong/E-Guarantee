package com.example.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisConfiguration {
    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    // @Bean
    // public LettuceConnectionFactory redisConnectionFactory() {
    // RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host,
    // port);
    // if (password != null && !password.isBlank()) {
    // config.setPassword(RedisPassword.of(password));
    // }
    // return new LettuceConnectionFactory(config);
    // }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {

        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host,
                port));
    }

}
