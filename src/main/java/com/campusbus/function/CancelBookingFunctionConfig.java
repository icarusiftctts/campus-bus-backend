package com.campusbus.function;

import com.campusbus.lambda.CancelBookingHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class CancelBookingFunctionConfig {

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> cancelBooking() {
        return event -> {
            CancelBookingHandler handler = new CancelBookingHandler();
            return handler.handleRequest(event, null);
        };
    }
}