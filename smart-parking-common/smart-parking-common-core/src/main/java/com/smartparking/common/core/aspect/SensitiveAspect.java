package com.smartparking.common.core.aspect;

import com.smartparking.common.core.annotation.Sensitive;
import com.smartparking.common.core.domain.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * 响应体字段脱敏 AOP：对 @RestController 返回值中带 @Sensitive 的字段做掩码。
 * 只处理标记了 @Sensitive 注解的字段，无标记的对象直接跳过。
 */
@Aspect
@Component
public class SensitiveAspect {

    private static final Logger log = LoggerFactory.getLogger(SensitiveAspect.class);

    @Around("execution(* com.smartparking..controller..*Controller.*(..))")
    public Object mask(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            process(result);
        } catch (Exception e) {
            log.warn("脱敏处理异常（不影响主流程）: {}", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private void process(Object obj) {
        if (obj == null) return;
        // Result 包装：取 data 递归
        if (obj instanceof Result) {
            process(((Result) obj).getData());
            return;
        }
        // 集合：逐个处理
        if (obj instanceof Collection) {
            for (Object item : (Collection) obj) process(item);
            return;
        }
        // Map：不处理
        if (obj instanceof Map) return;

        Class<?> clazz = obj.getClass();
        String pkg = clazz.getName();
        if (pkg.startsWith("java.") || pkg.startsWith("com.fasterxml.")
                || pkg.startsWith("org.springframework.")) return;

        // 扫描字段，没有 @Sensitive 直接跳过（性能优化）
        Field[] fields = clazz.getDeclaredFields();
        boolean hasSensitive = false;
        for (Field f : fields) {
            if (f.isAnnotationPresent(Sensitive.class)) { hasSensitive = true; break; }
        }
        if (!hasSensitive) return;

        for (Field f : fields) {
            Sensitive s = f.getAnnotation(Sensitive.class);
            if (s == null) continue;
            f.setAccessible(true);
            try {
                Object v = f.get(obj);
                if (v instanceof String) {
                    f.set(obj, mask((String) v, s.type()));
                }
            } catch (IllegalAccessException ignored) {}
        }
    }

    private String mask(String s, String type) {
        if (s == null || s.isEmpty()) return s;
        return switch (type) {
            case "phone", "mobile" -> s.length() == 11 ? s.substring(0, 3) + "****" + s.substring(7) : maskDefault(s);
            case "idcard" -> s.length() >= 8 ? s.substring(0, 4) + "**********" + s.substring(s.length() - 4) : maskDefault(s);
            case "plate" -> s.length() >= 3 ? s.substring(0, 1) + "****" + s.substring(s.length() - 1) : maskDefault(s);
            case "name" -> s.length() <= 1 ? s : s.charAt(0) + "*".repeat(Math.max(1, s.length() - 1));
            case "email" -> s.contains("@") ? s.charAt(0) + "***@" + s.substring(s.indexOf('@') + 1) : maskDefault(s);
            default -> maskDefault(s);
        };
    }

    private String maskDefault(String s) {
        return s.length() <= 2 ? "**" : s.charAt(0) + "**" + s.charAt(s.length() - 1);
    }
}
