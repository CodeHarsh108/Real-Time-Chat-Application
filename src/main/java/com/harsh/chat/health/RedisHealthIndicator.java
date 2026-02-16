package com.harsh.chat.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();

            if ("PONG".equals(pong)) {

                LettuceConnectionFactory factory =
                        (LettuceConnectionFactory) redisTemplate.getConnectionFactory();

                return Health.up()
                        .withDetail("status", "connected")
                        .withDetail("host", factory.getHostName())
                        .withDetail("port", factory.getPort())
                        .build();
            }

            return Health.down()
                    .withDetail("status", "disconnected")
                    .build();

        } catch (Exception e) {

            return Health.down(e)
                    .withDetail("status", "error")
                    .withDetail("message", e.getMessage())
                    .build();
        }

    }
}
