

package com.example.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Swagger配置
 *
 * @author zhougaojun
 */
@Configuration
public class Knife4jConfig {
    @Bean
    public OpenAPI createRestApi() {
        return new OpenAPI()
                .info(apiInfo())
                .security(security());
    }

    private Info apiInfo() {
        return new Info()
                .title("Agent智能体稳定")
                .description("Agent智能体稳定")
                .version("1.x");
    }

    private List<SecurityRequirement> security() {
        SecurityRequirement key = new SecurityRequirement();
        key.addList("Authorization", "Authorization");

        List<SecurityRequirement> list = new ArrayList<>();
        list.add(key);
        return list;
    }
}
