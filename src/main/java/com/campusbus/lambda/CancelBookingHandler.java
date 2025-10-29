package com.campusbus.lambda;

import com.campusbus.entity.Booking;
import com.campusbus.repository.BookingRepository;
import com.campusbus.repository.StudentRepository;
import com.campusbus.repository.TripRepository;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CancelBookingHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static BookingRepository bookingRepository;
    private static TripRepository tripRepository;
    private static StudentRepository studentRepository;
    private static RedisTemplate<String, String> redisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            System.setProperty("spring.main.lazy-initialization", "true");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            bookingRepository = context.getBean(BookingRepository.class);
            tripRepository = context.getBean(TripRepository.class);
            studentRepository = context.getBean(StudentRepository.class);
            try {
                redisTemplate = context.getBean(RedisTemplate.class);
            } catch (Exception e) {
                redisTemplate = null;
            }
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            // Extract bookingId from path parameters
            Map<String, Object> pathParams = (Map<String, Object>) event.get("pathParameters");
            if (pathParams == null) {
                return createErrorResponse(400, "Missing bookingId in path");
            }
            String bookingId = (String) pathParams.get("bookingId");

            // Validate JWT token and extract student ID
            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return createErrorResponse(401, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateAuthToken(token);
            if (!(Boolean) tokenData.get("valid")) {
                return createErrorResponse(401, "Invalid or expired token");
            }

            String studentId = (String) tokenData.get("studentId");

            boolean result = cancelBooking(bookingId, studentId);
            if (result) {
                return createSuccessResponse(200, "Booking cancelled and waitlist promoted");
            } else {
                return createErrorResponse(400, "Cancellation failed - booking not found or unauthorized");
            }

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    @Transactional
    public boolean cancelBooking(String bookingId, String studentId) {
        // 1. Acquire Redis lock to prevent concurrent cancellations on same trip
        Booking currentBooking = bookingRepository.findById(bookingId).orElse(null);
        if (currentBooking == null || !currentBooking.getStudentId().equals(studentId)) {
            return false; // Not authorized or not found
        }

        String tripId = currentBooking.getTripId();
        String lockKey = "cancel:" + tripId;
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "cancel", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(lockAcquired)) {
            return false; // Another cancellation in progress
        }

        try {
            // 2. Verify booking status (cannot cancel if scanned/already cancelled)
            if ("SCANNED".equals(currentBooking.getStatus()) ||
                    "CANCELLED".equals(currentBooking.getStatus())) {
                return false;
            }

            // 3. Mark current booking as cancelled
            currentBooking.setStatus("CANCELLED");
            bookingRepository.save(currentBooking);

            // 4. Promote next waitlisted user (if any)
            bookingRepository.promoteNextWaitlist(tripId);

            // 5. Update remaining waitlist positions
            bookingRepository.updateWaitlistPositions(tripId);

            return true;

        } finally {
            // 6. Always release the lock
            redisTemplate.delete(lockKey);
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}