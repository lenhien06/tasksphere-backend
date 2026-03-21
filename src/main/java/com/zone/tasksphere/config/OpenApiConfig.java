package com.zone.tasksphere.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taskSphereOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("TaskSphere API")
                .description("""
                    ## Hệ thống Quản lý Công việc & Dự án — TaskSphere
                    
                    Nền tảng SaaS Agile/Scrum dành cho nhóm phát triển phần mềm.
                    
                    ### Xác thực
                    Tất cả API (trừ `/auth/*`) yêu cầu **Bearer JWT Token**
                    trong header: `Authorization: Bearer {accessToken}`
                    
                    ### Phân quyền
                    - **ADMIN**: Quyền hệ thống — quản lý tài khoản, xem mọi dự án
                    - **PM** (Project Manager): Quyền cao nhất trong dự án
                    - **MEMBER**: Thành viên — làm việc trên task được giao
                    - **VIEWER**: Chỉ xem — không tạo/sửa được gì
                    
                    ### Response Format
                    ```json
                    {
                      "data": { },
                      "meta": {
                        "timestamp": "2026-03-17T07:00:00Z",
                        "version": "v1"
                      }
                    }
                    ```
                    
                    ### Error Format (RFC 7807)
                    ```json
                    {
                      "type": "https://tasksphere.io/errors/VALIDATION",
                      "title": "Validation Failed",
                      "status": 400,
                      "detail": "title: không được để trống"
                    }
                    ```
                    """)
                .version("v1.0.0")
                .contact(new Contact()
                    .name("TaskSphere Team")
                    .email("support@tasksphere.io"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://tasksphere.io/terms")))
            .addSecurityItem(new SecurityRequirement()
                .addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Nhập Access Token (không cần prefix 'Bearer')")));
    }
}
