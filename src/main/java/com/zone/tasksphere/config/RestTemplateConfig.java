package com.zone.tasksphere.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate dùng chung cho tất cả external API call.
     * connectTimeout: 5s, readTimeout: 10s.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        // Ensure Jackson message converter is present for JSON serialization
        restTemplate.getMessageConverters().add(0, new MappingJackson2HttpMessageConverter(new ObjectMapper()));
        return restTemplate;
    }
}
