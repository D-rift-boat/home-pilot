package com.dboat.iot.aspect;

import com.dboat.iot.utils.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class ApiLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiLogAspect.class);

    @Pointcut("execution(* com.dboat.iot.device.api.*.*(..))")
    public void apiPointcut() {}

    @Around("apiPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        String url = "unknown";
        String method = "unknown";
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            url = request.getRequestURI();
            method = request.getMethod();
        }

        String args = getArgsString(joinPoint.getArgs());
        log.info("[API] {} {} | Request: {}", method, url, args);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[API] {} {} | Error: {} | Elapsed: {}ms", method, url, e.getMessage(), elapsed);
            throw e;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        String response = JsonUtils.toJSONString(result);
        log.info("[API] {} {} | Response: {} | Elapsed: {}ms", method, url, response, elapsed);

        return result;
    }

    private String getArgsString(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            try {
                // Skip HttpServletRequest/Response objects
                if (args[i] instanceof HttpServletRequest) {
                    sb.append("HttpServletRequest");
                } else {
                    sb.append(JsonUtils.toJSONString(args[i]));
                }
            } catch (Exception e) {
                sb.append(args[i].toString());
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
