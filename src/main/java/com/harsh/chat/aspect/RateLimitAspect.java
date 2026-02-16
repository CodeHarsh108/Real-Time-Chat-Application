package com.harsh.chat.aspect;

import com.harsh.chat.annotation.RateLimit;
import com.harsh.chat.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RedisService redisService;

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String methodName = joinPoint.getSignature().getName();

        String key = rateLimit.key().isEmpty()
                ? username + ":" + methodName
                : rateLimit.key() + ":" + username;

        boolean allowed = redisService.checkRateLimit(key, rateLimit.maxAttempts(), rateLimit.timeWindow());

        if (!allowed) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        return joinPoint.proceed();
    }
}