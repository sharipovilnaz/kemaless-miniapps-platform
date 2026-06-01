package com.kemaless.paymentminiapp.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient.Builder restClientBuilder(MiniappPlatformProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(properties.sso().readTimeout());
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    @Qualifier("hostApiRestClient")
    RestClient hostApiRestClient(HostApiProperties hostApi) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(hostApi.readTimeout());
        return RestClient.builder().baseUrl(hostApi.baseUrl()).requestFactory(factory).build();
    }
}
