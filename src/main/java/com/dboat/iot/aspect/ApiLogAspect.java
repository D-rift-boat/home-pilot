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

/**
 * API 接口日志切面
 * <p>
 * 基于 AOP 拦截 Controller 层所有接口方法，自动记录：
 * <ul>
 *   <li>请求入参（HTTP 方法、URL、请求体 JSON）</li>
 *   <li>响应出参（响应体 JSON、接口耗时毫秒数）</li>
 *   <li>异常信息（接口执行异常时的错误日志）</li>
 * </ul>
 * 用于接口调用追踪、性能监控和问题排查。
 * </p>
 *
 * @author dboat
 */
@Aspect
@Component
public class ApiLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiLogAspect.class);

    /**
     * 定义切点：拦截 api 包下所有类的所有公共方法
     */
    @Pointcut("execution(* com.dboat.iot.api.*.*(..))")
    public void apiPointcut() {}

    /**
     * 环绕通知：在目标方法执行前后记录日志
     *
     * @param joinPoint 连接点（代表被拦截的方法）
     * @return 目标方法的返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("apiPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取当前 HTTP 请求信息
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        String url = "unknown";
        String method = "unknown";
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            url = request.getRequestURI();
            method = request.getMethod();
        }

        // 记录请求入参
        String args = getArgsString(joinPoint.getArgs());
        log.info("[API] {} {} | Request: {}", method, url, args);

        Object result;
        try {
            // 执行目标方法
            result = joinPoint.proceed();
        } catch (Throwable e) {
            // 记录异常日志并继续抛出
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[API] {} {} | Error: {} | Elapsed: {}ms", method, url, e.getMessage(), elapsed);
            throw e;
        }

        // 记录响应日志（含耗时）
        long elapsed = System.currentTimeMillis() - startTime;
        String response = JsonUtils.toJSONString(result);
        log.info("[API] {} {} | Response: {} | Elapsed: {}ms", method, url, response, elapsed);

        return result;
    }

    /**
     * 将方法参数序列化为可读字符串
     * <p>跳过 HttpServletRequest 等不可序列化的对象</p>
     *
     * @param args 方法参数数组
     * @return 格式化后的参数字符串
     */
    private String getArgsString(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            try {
                // 跳过 HttpServletRequest 等不可序列化的对象
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
