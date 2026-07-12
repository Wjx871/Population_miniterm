package com.example.population.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置。
 * <p>
 * 安全策略：
 * <ul>
 *   <li><b>禁止通配符 + 凭据</b>（{@code allowedOriginPatterns="*"} + {@code allowCredentials=true}
 *       是教科书级 CORS 误配，会让任意网站携带 cookie/token 跨域调用 API）</li>
 *   <li>只允许白名单中的具体域名</li>
 *   <li>白名单通过 {@code cors.allowed-origins} 注入（生产环境务必从环境变量配置）</li>
 *   <li>默认 {@code allowCredentials=false}，如需 cookie 跨域需手动开启</li>
 * </ul>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${cors.allowed-origins:}")
    private String allowedOriginsRaw;

    @Value("${cors.allow-credentials:false}")
    private boolean allowCredentials;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = parseOrigins(allowedOriginsRaw);
        if (origins.isEmpty()) {
            log.warn("[CORS] cors.allowed-origins 未配置，所有跨域请求将被拒绝。"
                    + "请通过环境变量 CORS_ALLOWED_ORIGINS 配置，例如："
                    + "https://admin.example.com,https://ops.example.com");
            return;
        }
        // 拒绝通配符：若配置中含 "*"，则强制关闭凭据并记录警告
        boolean hasWildcard = origins.contains("*");
        boolean effectiveAllowCredentials = allowCredentials && !hasWildcard;
        if (hasWildcard) {
            log.warn("[CORS] 检测到 allowed-origins 含 '*'，已自动关闭 allowCredentials 以避免通配符+凭据组合的 CSRF 风险");
        }

        var mapping = registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(effectiveAllowCredentials)
                .maxAge(3600);

        log.info("[CORS] 初始化完成: origins={}, allowCredentials={}",
                origins, effectiveAllowCredentials);
    }

    private List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}