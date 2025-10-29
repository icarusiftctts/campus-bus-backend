package com.campusbus.function;

import com.campusbus.lambda.GetStudentBookingsHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class GetStudentBookingsFunctionConfig {
    @Bean
    public Function<Map<String, Object>, Map<String, Object>> getStudentBookings() {
        GetStudentBookingsHandler handler = new GetStudentBookingsHandler();
        return event -> handler.handleRequest(event, null);
    }
}
