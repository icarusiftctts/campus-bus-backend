package com.campusbus.function;

import com.campusbus.lambda.GetUserProfileHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class GetUserProfileFunctionConfig {
    @Bean
    public Function<Map<String, Object>, Map<String, Object>> getUserProfile() {
        GetUserProfileHandler handler = new GetUserProfileHandler();
        return event -> handler.handleRequest(event, null);
    }
}
