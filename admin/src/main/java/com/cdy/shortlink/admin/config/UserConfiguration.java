package com.cdy.shortlink.admin.config;

import com.cdy.shortlink.admin.common.biz.user.UserFlowRiskControlFilter;
import com.cdy.shortlink.admin.common.biz.user.UserTransmitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 用户配置自动装配
 */
@Configuration
public class UserConfiguration {

    /**
     * 用户信息传递过滤器
     */
    @Bean
    public FilterRegistrationBean<UserTransmitFilter> globalUserTransmitFilter() {
        FilterRegistrationBean<UserTransmitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserTransmitFilter());
        registration.addUrlPatterns("/*");   // 对所有请求路径生效
        registration.setOrder(0);            // 优先级最高，最早执行
        return registration;
    }

    /**
     * 流量风控 Lua 脚本：由容器创建一次并复用，避免每个请求都重新读取、编译
     */
    @Bean
    public DefaultRedisScript<Long> userFlowRiskControlScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/user_flow_risk_control.lua")));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 用户操作流量风控过滤器
     */
    @Bean
    @ConditionalOnProperty(name = "short-link.flow-limit.enable", havingValue = "true")
    public FilterRegistrationBean<UserFlowRiskControlFilter> globalUserFlowRiskControlFilter(
            StringRedisTemplate stringRedisTemplate,
            UserFlowRiskControlConfiguration userFlowRiskControlConfiguration,
            DefaultRedisScript<Long> userFlowRiskControlScript) {
        FilterRegistrationBean<UserFlowRiskControlFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserFlowRiskControlFilter(
                stringRedisTemplate, userFlowRiskControlConfiguration, userFlowRiskControlScript));
        registration.addUrlPatterns("/*");
        registration.setOrder(10);
        return registration;
    }
}

