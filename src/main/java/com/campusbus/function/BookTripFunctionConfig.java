package com.campusbus.function;

import com.campusbus.lambda.BookTripHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class BookTripFunctionConfig {

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> bookTrip() {
        return event -> {
            BookTripHandler handler = new BookTripHandler();
            return handler.handleRequest(event, null);
        };
    }
}