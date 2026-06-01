package com.kemaless.core;

import com.kemaless.core.ai.config.PlatformAiProperties;
import com.kemaless.core.auth.config.PlatformAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableConfigurationProperties({PlatformAuthProperties.class, PlatformAiProperties.class})
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }

}
