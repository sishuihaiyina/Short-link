package com.cdy.shortlink.admin.common.biz.user;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.List;

/**
 * 用户信息传输过滤器
 * 处理业务代码怎么知道"当前是谁在操作"？的问题
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {


    private final StringRedisTemplate stringRedisTemplate;

    private static final List<String> IGNORE_URLS = Lists.newArrayList(
            "/api/short-link/admin/v1/user/login",
            "/api/short-link/admin/v1/user/has-username");


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String userName = httpServletRequest.getHeader("username");
        String token = httpServletRequest.getHeader("token");
        // 只有请求头里带了用户名和 token，才去 Redis 查登录态；否则跳过（如登录、注册接口本就不带）
        if (StrUtil.isNotBlank(userName) && StrUtil.isNotBlank(token)) {
            Object userInfoJsonStr = stringRedisTemplate.opsForHash().get("login_" + userName, token);
            if (userInfoJsonStr != null) {
                UserInfoDTO userInfoDTO = JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
                UserContext.setUser(userInfoDTO);
            }
        }
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            UserContext.removeUser();
        }
    }
}
