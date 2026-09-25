package org.dromara.warm.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置，为本地联调的前端放开跨域，避免跨域直连被拦成 403。
 *
 * @author may
 * @since 2026/9/5
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // 引擎的办理权限校验需要当前请求用户，这里按请求写入并清理，避免线程复用串号
        registry.addInterceptor(new DemoUserInterceptor()).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
            // 按来源模式放开：vite 端口被占用时会顺延（5174 等），也可能用局域网地址打开页面，
            // 写死 5173 会让带 Origin 的 POST/PUT/DELETE（导入、发布、复制、删除）被判为非法跨域而返回 403。
            // 本 demo 仅用于本机联调，正式集成请自行收紧来源白名单。
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            // 流程定义导出走文件下载，前端需要读取文件名
            .exposedHeaders("Content-Disposition")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
