package com.cdy.shortlink.project.common.web;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 内部接口鉴权过滤器
 * <p>
 * project 的 8001 同时承担「短链跳转」和「内部 API」两个角色：跳转必须对公网开放，
 * 但 /api/** 只应该由 admin 通过 Feign 调用。以前这两个角色混在一起，
 * 任何人直接访问 http://域名:8001/api/short-link/v1/create 就能绕过网关和 admin 的归属校验。
 * <p>
 * 现在 /api/** 必须携带内部凭证头，凭证由 admin 的 Feign 拦截器统一注入。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalApiAuthFilter implements Filter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final String API_PATH_PREFIX = "/api/";

    private static final String FORBIDDEN_BODY = "{\"code\":\"A000403\",\"message\":\"禁止直接访问内部接口\",\"success\":false}";

    @Value("${short-link.internal.token}")
    private String internalToken;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        // 短链跳转 /{code}、/page/** 要对外，直接放行
        if (!path.startsWith(API_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        if (StrUtil.equals(internalToken, httpRequest.getHeader(INTERNAL_TOKEN_HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        log.warn("拒绝直连内部接口，path：{}，remoteAddr：{}", path, httpRequest.getRemoteAddr());
        httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.getOutputStream().write(FORBIDDEN_BODY.getBytes(StandardCharsets.UTF_8));
    }
}
