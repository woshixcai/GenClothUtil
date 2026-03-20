package com.UiUtil.config;

import com.UiUtil.shared.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    /**
     * 白名单：不需要登录即可访问的路径。
     * 拦截器对所有路径生效，白名单内的路径直接放行。
     */
    private static final String[] WHITE_LIST = {
            "/auth/login",       // 登录
            "/auth/logout",      // 退出（无状态，不需要验证）
            "/",                 // 静态首页
            "/index.html",
            "/static/**",        // 静态资源
            "/css/**",
            "/js/**",
            "/favicon.ico",
            "/TestController/test"  // 健康检查接口
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(WHITE_LIST);
    }
}
