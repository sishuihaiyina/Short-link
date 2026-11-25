package com.cdy.shortlink.project.common.config;

import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * ShardingSphere 数据源配置
 * <p>
 * 没有直接用 {@code jdbc:shardingsphere:classpath:...} 驱动，是因为它解析 YAML 时不支持 {@code ${}}
 * 占位符，数据库账号密码只能明文写死在 yaml 里。这里改成：先把 YAML 读成字符串，
 * 交给 Spring 解析占位符（值来自环境变量），再交给 ShardingSphere 建数据源。
 * 没配环境变量时会在解析阶段直接抛异常，启动即失败，不会带病运行。
 */
@Configuration
public class ShardingSphereDataSourceConfiguration {

    @Bean
    public DataSource dataSource(Environment environment) throws IOException, SQLException {
        String location = environment.getProperty(
                "short-link.datasource.config-location",
                "classpath:shardingsphere-config-" + environment.getProperty("database.env", "dev") + ".yaml");
        Resource resource = new DefaultResourceLoader().getResource(location);
        String yaml;
        try (InputStream inputStream = resource.getInputStream()) {
            yaml = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
        String resolvedYaml = environment.resolvePlaceholders(yaml);
        return YamlShardingSphereDataSourceFactory.createDataSource(resolvedYaml.getBytes(StandardCharsets.UTF_8));
    }
}
