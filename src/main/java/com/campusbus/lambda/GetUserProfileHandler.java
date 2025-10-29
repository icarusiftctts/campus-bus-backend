package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Booking;
import com.campusbus.entity.Student;
import com.campusbus.entity.Trip;
import com.campusbus.repository.BookingRepository;
import com.campusbus.repository.StudentRepository;
import com.campusbus.repository.TripRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

public class GetUserProfileHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static ConfigurableApplicationContext context;
    private static StudentRepository studentRepository;
    private static BookingRepository bookingRepository;
    private static TripRepository tripRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            studentRepository = context.getBean(StudentRepository.class);
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
            Student student = studentRepository.findById(studentId).orElse(null);
            
            if (student == null) {
                return createErrorResponse(404, "Student not found");
            }

            List<Booking> activeBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getStudentId().equals(studentId))
                .filter(b -> Arrays.asList("CONFIRMED", "WAITLIST", "SCANNED").contains(b.getStatus()))
                .collect(Collectors.toList());

            List<Map<String, Object>> bookingsWithTrips = new ArrayList<>();
            for (Booking booking : activeBookings) {
                Trip trip = tripRepository.findById(booking.getTripId()).orElse(null);
                if (trip != null) {
                    Map<String, Object> bookingMap = new HashMap<>();
                    bookingMap.put("bookingId", booking.getBookingId());
                    bookingMap.put("tripId", booking.getTripId());
                    bookingMap.put("status", booking.getStatus());
                    bookingMap.put("waitlistPosition", booking.getWaitlistPosition() != null ? booking.getWaitlistPosition() : 0);
                    bookingMap.put("route", trip.getRoute());
                    bookingMap.put("tripDate", trip.getTripDate().toString());
                    bookingMap.put("departureTime", trip.getDepartureTime().toString());
                    bookingMap.put("qrToken", booking.getQrToken() != null ? booking.getQrToken() : "");
                    bookingsWithTrips.add(bookingMap);
                }
            }

            Map<String, Object> profileData = new HashMap<>();
            profileData.put("studentId", student.getStudentId());
            profileData.put("email", student.getEmail());
            profileData.put("name", student.getName());
            profileData.put("room", student.getRoom() != null ? student.getRoom() : "");
            profileData.put("phone", student.getPhone() != null ? student.getPhone() : "");
            profileData.put("penaltyCount", student.getPenaltyCount());
            profileData.put("isBlocked", student.isBlocked());
            profileData.put("profileComplete", student.getRoom() != null && student.getPhone() != null);
            profileData.put("activeBookings", bookingsWithTrips);

            return createSuccessResponse(200, profileData);

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
