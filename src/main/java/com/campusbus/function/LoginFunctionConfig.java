package com.campusbus.function;

import com.campusbus.lambda.LoginHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class LoginFunctionConfig {

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> login() {
        return event -> {
            LoginHandler handler = new LoginHandler();
            return handler.handleRequest(event, null);
        };
    }
}