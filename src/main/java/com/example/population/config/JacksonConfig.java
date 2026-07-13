package com.example.population.config;

import com.example.population.util.Masked;
import com.example.population.util.MaskedSerializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Jackson 全局配置。
 * <p>
 * 注册 {@link MaskedSerializer}，让 {@link Masked} 注解在 DTO/VO 上自动生效。
 * 实现思路：override {@code changeProperties}（Jackson 2.10+ 推荐入口），
 * 对每个标注了 {@link Masked} 的字段，把它的 BeanPropertyWriter 替换为带脱敏 serializer 的实例。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer maskedCustomizer() {
        SimpleModule module = new SimpleModule("PopulationMaskedModule");
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                             BeanDescription beanDesc,
                                                             List<BeanPropertyWriter> beanProperties) {
                for (int i = 0; i < beanProperties.size(); i++) {
                    BeanPropertyWriter writer = beanProperties.get(i);
                    Masked ann = writer.getAnnotation(Masked.class);
                    if (ann == null) {
                        ann = writer.getContextAnnotation(Masked.class);
                    }
                    if (ann != null) {
                        JsonSerializer<Object> maskSer = new MaskedSerializer(ann.value());
                        beanProperties.set(i, new MaskedBeanPropertyWriter(writer, maskSer));
                    }
                }
                return beanProperties;
            }
        });

        return builder -> builder.modulesToInstall(module);
    }

    /**
     * 把指定 serializer 注入到 BeanPropertyWriter 的内部 _serializer 字段。
     * <p>
     * BeanPropertyWriter 没有公开的 assignSerializer 的子类化入口，
     * 但其 protected _serializer 字段可通过子类 + 反射设置。
     */
    public static final class MaskedBeanPropertyWriter extends BeanPropertyWriter {
        private static final long serialVersionUID = 1L;

        public MaskedBeanPropertyWriter(BeanPropertyWriter base, JsonSerializer<Object> ser) {
            super(base);
            this._serializer = ser;
        }
    }
}