package com.example.population.util;

import com.example.population.exception.BizException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;

/**
 * 统一脱敏序列化器。
 * <p>
 * 用法：在 DTO 字段上标 {@link Masked}，序列化时按 {@link MaskedRule} 选择脱敏规则。
 *
 * <pre>
 *   &#064;Masked(MaskedRule.PHONE)
 *   private String phone;
 *
 *   // 输出：138****5678
 * </pre>
 *
 * <p><b>管理员透出机制</b>：
 * 当前线程为 L3 (permissionLevel &gt;= 3) 且调用方在 thread-local 上标记 "unmask"，
 * 则跳过脱敏（仅在 Controller 显式设置时生效）。默认一律脱敏。
 */
public class MaskedSerializer extends StdScalarSerializer<Object> implements ContextualSerializer {

    private final MaskedRule rule;

    /** 当前线程是否跳过脱敏（L3 管理员 + 显式开关）。 */
    public static final ThreadLocal<Boolean> UNMASK = new ThreadLocal<>();

    public MaskedSerializer() {
        super(Object.class);
        this.rule = MaskedRule.ID_CARD;
    }

    public MaskedSerializer(MaskedRule rule) {
        super(Object.class);
        this.rule = rule;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property == null) return this;
        Masked ann = property.getAnnotation(Masked.class);
        if (ann == null) {
            ann = property.getContextAnnotation(Masked.class);
        }
        if (ann == null) return this;
        return new MaskedSerializer(ann.value());
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        Boolean un = UNMASK.get();
        if (Boolean.TRUE.equals(un)) {
            gen.writeString(value.toString());
            return;
        }
        String s = value.toString();
        gen.writeString(mask(s, rule));
    }

    public static String mask(String raw, MaskedRule rule) {
        if (raw == null) return null;
        switch (rule) {
            case ID_CARD:
                return IdentityMasker.maskIdCard(raw);
            case PHONE:
                return IdentityMasker.maskPhone(raw);
            case NAME:
                return IdentityMasker.maskName(raw);
            case ADDRESS:
                return maskAddress(raw);
            case FULL:
                return "****";
            default:
                throw new BizException(500, "不支持的脱敏规则: " + rule);
        }
    }

    private static String maskAddress(String addr) {
        if (addr == null || addr.isEmpty()) return null;
        if (addr.length() <= 8) return "****";
        // 保留前 6 后 4
        return addr.substring(0, 6) + "****" + addr.substring(addr.length() - 4);
    }
}