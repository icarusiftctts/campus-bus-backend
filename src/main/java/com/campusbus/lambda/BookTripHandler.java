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
import com.campusbus.util.QRCodeGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class BookTripHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static BookingRepository bookingRepository;
    private static TripRepository tripRepository;
    private static StudentRepository studentRepository;
    private static RedisTemplate<String, Object> redisTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            bookingRepository = context.getBean(BookingRepository.class);
            tripRepository = context.getBean(TripRepository.class);
            studentRepository = context.getBean(StudentRepository.class);
            redisTemplate = context.getBean(RedisTemplate.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

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

            // Check student penalty status
            Optional<Student> studentOpt = studentRepository.findById(studentId);
            if (studentOpt.isEmpty()) {
                return createErrorResponse(404, "Student not found");
            }

            Student student = studentOpt.get();
            if (student.isBlocked()) {
                return createErrorResponse(403, "Account blocked due to penalties");
            }

            // Parse and validate request body
            String requestBody = (String) event.get("body");
            if (requestBody == null || requestBody.trim().isEmpty()) {
                return createErrorResponse(400, "Missing request body");
            }

            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);
            String tripId = (String) body.get("tripId");
            if (tripId == null || tripId.trim().isEmpty()) {
                return createErrorResponse(400, "Missing tripId parameter");
            }

            // Validate trip exists before acquiring expensive Redis lock
            Optional<Trip> tripOpt = tripRepository.findById(tripId);
            if (tripOpt.isEmpty()) {
                return createErrorResponse(404, "Trip not found");
            }

            // Check if student already has booking for this trip
            Optional<Booking> existingBooking = bookingRepository.findByStudentIdAndTripId(studentId, tripId);
            if (existingBooking.isPresent()) {
                return createErrorResponse(400, "Already booked for this trip");
            }

            // NOW acquire Redis lock after all validations pass
            String lockKey = "booking:" + tripId;
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, studentId, Duration.ofSeconds(30));
            if (!lockAcquired) {
                return createErrorResponse(409, "Booking in progress, please try again");
            }

            try {
                Trip trip = tripOpt.get();
                
                // Re-check booking count inside lock to ensure accuracy
                Optional<Booking> reCheckBooking = bookingRepository.findByStudentIdAndTripId(studentId, tripId);
                if (reCheckBooking.isPresent()) {
                    return createErrorResponse(400, "Already booked for this trip");
                }
                
                int confirmedCount = bookingRepository.countByTripIdAndStatus(tripId, "CONFIRMED");
                int availableSeats = trip.getCapacity() - trip.getFacultyReserved() - confirmedCount;

                String bookingId = "B" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Booking booking = new Booking(bookingId, studentId, tripId, "CONFIRMED");

                if (availableSeats > 0) {
                    // Confirmed booking - generate QR token
                    String qrToken = QRCodeGenerator.generateQRToken(bookingId, tripId, studentId);
                    booking.setQrToken(qrToken);
                    bookingRepository.save(booking);
                    
                    Map<String, Object> responseData = Map.of(
                        "bookingId", bookingId,
                        "status", "CONFIRMED",
                        "qrToken", qrToken,
                        "message", "Seat confirmed"
                    );
                    return createSuccessResponse(201, responseData);
                } else {
                    // Waitlist booking - use atomic position assignment
                    booking.setStatus("WAITLIST");
                    bookingRepository.save(booking); // Save first to get DB row

                    // Atomically assign position using database-level operation
                    int position = bookingRepository.assignAtomicWaitlistPosition(tripId, bookingId);

                    Map<String, Object> responseData = Map.of(
                            "bookingId", bookingId,
                            "status", "WAITLIST",
                            "position", position,
                            "message", "Added to waitlist"
                    );
                    return createSuccessResponse(201, responseData);
                }

            } finally {
                // Always release the Redis lock
                redisTemplate.delete(lockKey);
            }

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}