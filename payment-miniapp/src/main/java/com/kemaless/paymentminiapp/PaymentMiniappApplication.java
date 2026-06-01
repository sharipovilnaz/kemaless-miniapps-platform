package com.kemaless.paymentminiapp;

import com.kemaless.paymentminiapp.config.HostApiProperties;
import com.kemaless.paymentminiapp.config.MiniappPlatformProperties;
import com.kemaless.paymentminiapp.config.MiniappWebSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({MiniappPlatformProperties.class, HostApiProperties.class, MiniappWebSecurityProperties.class})
public class PaymentMiniappApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentMiniappApplication.class, args);
    }
}
