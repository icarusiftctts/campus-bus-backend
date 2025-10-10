package com.campusbus.function;

import com.campusbus.lambda.ValidateQRHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class ValidateQRFunctionConfig {

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> validateQR() {
        return event -> {
            ValidateQRHandler handler = new ValidateQRHandler();
            return handler.handleRequest(event, null);
        };
    }
}