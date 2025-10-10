package com.campusbus.function;

import com.campusbus.lambda.CreateTripHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class CreateTripFunctionConfig {

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> createTrip() {
        return event -> {
            CreateTripHandler handler = new CreateTripHandler();
            return handler.handleRequest(event, null);
        };
    }
}