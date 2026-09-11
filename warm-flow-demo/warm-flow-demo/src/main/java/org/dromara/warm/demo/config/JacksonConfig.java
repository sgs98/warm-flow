package org.dromara.warm.demo.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Demo JSON 序列化配置。
 *
 * <p>Warm-Flow 使用雪花算法生成 Long 类型 ID，统一序列化为字符串，避免
 * JavaScript number 超过安全整数范围后发生精度丢失。</p>
 *
 * @author may
 * @since 2026/9/5
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册 Long 安全整数序列化器，避免前端 ID 精度丢失。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
            .serializerByType(Long.class, SafeLongSerializer.INSTANCE)
            .serializerByType(Long.TYPE, SafeLongSerializer.INSTANCE);
    }

    /**
     * 仅将超出 JavaScript 安全整数范围的 Long 转成字符串，普通计数值仍保持 number。
     */
    private static final class SafeLongSerializer extends JsonSerializer<Long> {

        private static final long MAX_SAFE_INTEGER = 9007199254740991L;
        private static final SafeLongSerializer INSTANCE = new SafeLongSerializer();

        @Override
        public void serialize(Long value, JsonGenerator generator, SerializerProvider provider)
            throws java.io.IOException {
            if (value != null && (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER)) {
                generator.writeString(value.toString());
            } else {
                generator.writeNumber(value);
            }
        }
    }
}
