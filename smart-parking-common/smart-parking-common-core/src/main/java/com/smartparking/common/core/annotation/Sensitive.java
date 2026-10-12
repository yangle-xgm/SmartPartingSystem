package com.smartparking.common.core.annotation;

import java.lang.annotation.*;

/**
 * 字段脱敏标记：标注在 DTO/VO 字段上，AOP 在序列化前按 type 脱敏。
 * type: phone/mobile, idcard, plate, name, address, email, default
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
    String type() default "default";
}
