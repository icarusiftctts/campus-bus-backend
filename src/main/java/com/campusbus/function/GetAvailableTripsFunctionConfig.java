package com.campusbus.function;

import com.campusbus.lambda.GetAvailableTripsHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class GetAvailableTripsFunctionConfig {

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> getAvailableTrips() {
        return event -> {
            GetAvailableTripsHandler handler = new GetAvailableTripsHandler();
            return handler.handleRequest(event, null);
        };
    }
}