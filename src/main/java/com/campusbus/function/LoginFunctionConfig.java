package com.campusbus.function;

import com.campusbus.lambda.LoginUserHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class LoginFunctionConfig {

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> login() {
        return event -> {
            LoginUserHandler handler = new LoginUserHandler();
            return handler.handleRequest(event, null);
        };
    }
}