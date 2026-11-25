package com.cdy.shortlink.admin.remote;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Feign 内部调用凭证
 * <p>
 * project 的 /api/** 现在要求携带 X-Internal-Token，admin 是唯一合法的调用方，
 * 在这里统一注入，业务代码不用关心。
 * <p>
 * 注意：这里刻意不加 @Configuration —— 加了之后会被组件扫描成全局配置，
 * 对所有 Feign 客户端生效；不标注时它只会作为 {@link ShortLinkActualRemoteService} 的专属配置生效。
 */
public class InternalApiFeignConfiguration {

    @Value("${short-link.internal.token}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return requestTemplate -> requestTemplate.header("X-Internal-Token", internalToken);
    }
}
