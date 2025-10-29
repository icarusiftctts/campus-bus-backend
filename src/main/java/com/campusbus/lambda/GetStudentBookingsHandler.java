package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Booking;
import com.campusbus.entity.Trip;
import com.campusbus.repository.BookingRepository;
import com.campusbus.repository.TripRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

public class GetStudentBookingsHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static ConfigurableApplicationContext context;
    private static BookingRepository bookingRepository;
    private static TripRepository tripRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            bookingRepository = context.getBean(BookingRepository.class);
            tripRepository = context.getBean(TripRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            Map<String, String> headers = (Map<String, String>) event.get("headers");
            String authHeader = headers != null ? headers.get("Authorization") : null;
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return createErrorResponse(401, "Missing or invalid authorization header");
            }

            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateAuthToken(token);
            
            if (!(Boolean) tokenData.get("valid")) {
                return createErrorResponse(401, "Invalid or expired token");
            }

            String studentId = (String) tokenData.get("studentId");

            List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> b.getStudentId().equals(studentId))
                .collect(Collectors.toList());

            List<Map<String, Object>> bookingsWithTrips = new ArrayList<>();
            for (Booking booking : bookings) {
                Trip trip = tripRepository.findById(booking.getTripId()).orElse(null);
                if (trip != null) {
                    Map<String, Object> bookingMap = new HashMap<>();
                    bookingMap.put("bookingId", booking.getBookingId());
                    bookingMap.put("tripId", booking.getTripId());
                    bookingMap.put("status", booking.getStatus());
                    bookingMap.put("bookedAt", booking.getBookedAt().toString());
                    bookingMap.put("route", trip.getRoute());
                    bookingMap.put("tripDate", trip.getTripDate().toString());
                    bookingMap.put("departureTime", trip.getDepartureTime().toString());
                    bookingsWithTrips.add(bookingMap);
                }
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("bookings", bookingsWithTrips);
            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("statusCode", statusCode);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            response.put("headers", headers);
            
            response.put("body", objectMapper.writeValueAsString(data));
            return response;
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        response.put("headers", headers);
        
        response.put("body", "{\"message\":\"" + message + "\"}");
        return response;
    }
}
