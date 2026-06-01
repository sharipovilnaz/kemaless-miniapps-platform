package com.kemaless.miniappsdk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeStormSdkApplication {
	public static void main(String[] args) {
		SpringApplication.run(CodeStormSdkApplication.class, args);
	}
}
