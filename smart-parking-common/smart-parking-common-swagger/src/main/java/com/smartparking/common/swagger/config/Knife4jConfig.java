package com.smartparking.common.swagger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j API文档配置
 * 访问地址：http://localhost:{port}/doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("小区智慧停车管理系统 API")
                        .description("基于Spring Cloud微服务架构的智慧停车系统接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartParking Team")
                                .email("admin@smartparking.com")));
    }
}
