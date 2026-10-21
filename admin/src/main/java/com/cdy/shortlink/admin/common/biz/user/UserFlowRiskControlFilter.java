package com.cdy.shortlink.admin.common.biz.user;

import com.alibaba.fastjson2.JSON;
import com.cdy.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.cdy.shortlink.admin.common.convention.exception.ClientException;
import com.cdy.shortlink.admin.common.convention.result.Results;
import com.cdy.shortlink.admin.config.UserFlowRiskControlConfiguration;
import com.google.common.collect.Lists;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

/**
 * 用户操作流量风控过滤器
 */
@Slf4j
@RequiredArgsConstructor
public class UserFlowRiskControlFilter implements Filter {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserFlowRiskControlConfiguration userFlowRiskControlConfiguration;
    /**
     * Lua 脚本由 Spring 容器创建一次并复用，避免每个请求都重新读取、编译脚本
     */
    private final DefaultRedisScript<Long> userFlowRiskControlScript;

    @SneakyThrows
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        String username = Optional.ofNullable(UserContext.getUsername()).orElse("other");
        Long result;
        try {
            result = stringRedisTemplate.execute(userFlowRiskControlScript, Lists.newArrayList(username), userFlowRiskControlConfiguration.getTimeWindow());
        } catch (Throwable ex) {
            log.error("执行用户请求流量限制LUA脚本出错", ex);
            returnJson((HttpServletResponse) response, JSON.toJSONString(Results.failure(new ClientException(BaseErrorCode.FLOW_LIMIT_ERROR))));
            return;
        }
        if (result == null || result > userFlowRiskControlConfiguration.getMaxAccessCount()) {
            returnJson((HttpServletResponse) response, JSON.toJSONString(Results.failure(new ClientException(BaseErrorCode.FLOW_LIMIT_ERROR))));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void returnJson(HttpServletResponse response, String json) throws Exception {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.print(json);
        }
    }
}
