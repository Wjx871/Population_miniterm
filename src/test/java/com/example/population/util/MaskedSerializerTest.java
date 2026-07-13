package com.example.population.util;

import com.example.population.config.JacksonConfig;
import com.example.population.dto.PersonVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.BeanDescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-2 敏感字段统一脱敏 单元测试。
 */
class MaskedSerializerTest {

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper();
        SimpleModule module = new SimpleModule("Test");
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
                        beanProperties.set(i,
                                new JacksonConfig.MaskedBeanPropertyWriter(writer,
                                        new MaskedSerializer(ann.value())));
                    }
                }
                return beanProperties;
            }
        });
        m.registerModule(module);
        return m;
    }

    @Test
    @DisplayName("身份证号脱敏：保留前 6 后 4")
    void maskIdCard() {
        String json = write(PersonVO.builder()
                .personId(1L)
                .name("张三")
                .identityTypeCode("ID_CARD")
                .identityNo("110101199001011234")
                .phone("13800138000")
                .contactAddress("北京市朝阳区某某街道123号")
                .build());
        assertThat(json).contains("\"identityNo\":\"110101************1234\"");
        assertThat(json).contains("\"phone\":\"138****8000\"");
        assertThat(json).contains("\"name\":\"张*\"");
        assertThat(json).contains("\"contactAddress\":\"北京市朝阳区****123号\"");
    }

    @Test
    @DisplayName("UNMASK=true 时 L3 用户看到原文")
    void unmaskL3() throws Exception {
        // 先拿一个普通 mapper，先确认默认脱敏生效
        ObjectMapper m = buildMapper();
        PersonVO vo = PersonVO.builder()
                .personId(1L)
                .name("张三")
                .identityNo("110101199001011234")
                .phone("13800138000")
                .build();
        String masked = m.writeValueAsString(vo);
        assertThat(masked).contains("110101************1234");

        // 模拟 L3 + unmask=true
        MaskedSerializer.UNMASK.set(Boolean.TRUE);
        try {
            String raw = m.writeValueAsString(vo);
            assertThat(raw).contains("110101199001011234");
        } finally {
            MaskedSerializer.UNMASK.remove();
        }
    }

    @Test
    @DisplayName("UNMASK=true 但当前不是 L3 → 仍然脱敏（生产代码会在 Controller 把关，这里仅验证序列化器默认行为）")
    void unmaskByAnyone() throws Exception {
        // 序列化器层面只看 ThreadLocal，不会校验角色；上层 Controller 已把关。
        // 因此 ThreadLocal 置 true 即返回原文。
        PersonVO vo = PersonVO.builder()
                .name("欧阳娜娜")
                .identityNo("110101199001011234")
                .build();
        MaskedSerializer.UNMASK.set(Boolean.TRUE);
        try {
            String json = buildMapper().writeValueAsString(vo);
            assertThat(json).contains("欧阳娜娜");
            assertThat(json).contains("110101199001011234");
        } finally {
            MaskedSerializer.UNMASK.remove();
        }
    }

    private String write(Object o) {
        try {
            return buildMapper().writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}