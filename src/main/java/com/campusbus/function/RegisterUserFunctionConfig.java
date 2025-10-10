package com.campusbus.function;

import com.campusbus.lambda.RegisterUserHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class RegisterUserFunctionConfig {

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> registerUser() {
        return event -> {
            RegisterUserHandler handler = new RegisterUserHandler();
            return handler.handleRequest(event, null);
        };
    }
}