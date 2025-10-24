package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Booking;
import com.campusbus.repository.BookingRepository;
import com.campusbus.util.QRCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * VALIDATE QR HANDLER
 * 
 * Purpose: Validate QR codes and mark students as boarded (for bus operators)
 * 
 * API Gateway Integration:
 * - Method: POST
 * - Path: /api/qr/validate
 * - Authorization: Required (Operator JWT token - future enhancement)
 * 
 * Frontend Integration (Flutter - Operator App):
 * 1. Make HTTP POST request to: https://your-api-gateway-url/api/qr/validate
 * 2. Headers: {
 *      "Content-Type": "application/json",
 *      "Authorization": "Bearer <operatorToken>"
 *    }
 * 3. Body: {
 *      "qrToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *      "tripId": "T12AB34CD"
 *    }
 * 4. Success Response - Valid QR (200): {
 *      "valid": true,
 *      "status": "SCANNED",
 *      "bookingId": "B56EF78GH",
 *      "studentId": "S12AB34CD",
 *      "message": "QR code validated successfully"
 *    }
 * 5. Success Response - Already Scanned (200): {
 *      "valid": true,
 *      "status": "ALREADY_SCANNED", 
 *      "message": "QR code already scanned"
 *    }
 * 6. Error Response - Invalid QR (400): {
 *      "valid": false,
 *      "message": "Invalid QR code"
 *    }
 * 
 * Usage Flow (Bus Operator App):
 * - Operator opens QR scanner screen in Flutter app
 * - Points camera at student's QR code
 * - App extracts qrToken from scanned QR code
 * - Flutter sends POST request with qrToken and current tripId
 * - On valid QR: Show green checkmark, play success sound, mark student as boarded
 * - On invalid QR: Show red X, play error sound, display error message
 * - On already scanned: Show yellow warning, display "Already boarded" message
 * 
 * QR Code Security:
 * - QR tokens are cryptographically signed (cannot be forged)
 * - Tokens are trip-specific (QR for Trip A won't work for Trip B)
 * - Tokens expire 24 hours after trip completion
 * - Each QR can only be scanned once per trip
 * 
 * Operator App Features:
 * - Real-time passenger count updates
 * - List of boarded vs not-boarded students
 * - Manual check-in option for QR failures
 * - Offline QR validation (cache valid tokens)
 * - Sound/vibration feedback for scan results
 * 
 * Student App Integration:
 * - Students show QR code from their booking
 * - QR code displays student name and seat info
 * - QR code works offline (no internet needed for display)
 * - Color coding: Blue (not scanned) → Green (scanned) → Red (expired)
 */


public class ValidateQRHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static BookingRepository bookingRepository;
    private static RedisTemplate<String, String> redisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            bookingRepository = context.getBean(BookingRepository.class);
            redisTemplate = context.getBean(RedisTemplate.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            // Parse request body
            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);

            String qrToken = (String) body.get("qrToken");
            String tripId = (String) body.get("tripId");

            if (qrToken == null || tripId == null) {
                return createInvalidResponse("Missing qrToken or tripId");
            }

            // Validate QR token
            Map<String, Object> tokenData = QRCodeGenerator.validateQRToken(qrToken);
            if (!(Boolean) tokenData.get("valid")) {
                return createInvalidResponse("Invalid QR code");
            }

            String bookingId = (String) tokenData.get("bookingId");
            String tokenTripId = (String) tokenData.get("tripId");

            // Verify trip matches (critical security check)
            if (!tripId.equals(tokenTripId)) {
                return createInvalidResponse("QR code not valid for this trip");
            }

            // Acquire Redis lock to prevent concurrent scans of same booking
            String lockKey = "scan:" + bookingId;
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "scanning", 30, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(lockAcquired)) {
                return createInvalidResponse("QR scan in progress, try again");
            }

            try {
                // Validate booking exists and is valid for this trip
                Booking booking = bookingRepository.findBookingForValidation(bookingId, tripId);
                if (booking == null) {
                    return createInvalidResponse("Booking not found for this trip");
                }

                // Check if already scanned
                if ("SCANNED".equals(booking.getStatus())) {
                    Map<String, Object> responseData = Map.of(
                            "valid", true,
                            "status", "ALREADY_SCANNED",
                            "message", "QR code already scanned"
                    );
                    return createSuccessResponse(200, responseData);
                }

                // Check if booking is confirmed
                if (!"CONFIRMED".equals(booking.getStatus())) {
                    return createInvalidResponse("Booking not confirmed");
                }

                // Mark as scanned (with transaction protection)
                int updated = markBookingScanned(bookingId, tripId);
                if (updated > 0) {
                    Map<String, Object> responseData = Map.of(
                            "valid", true,
                            "status", "SCANNED",
                            "bookingId", bookingId,
                            "studentId", booking.getStudentId(),
                            "message", "QR code validated successfully"
                    );
                    return createSuccessResponse(200, responseData);
                } else {
                    return createInvalidResponse("Failed to update booking status");
                }

            } finally {
                // Always release the lock
                redisTemplate.delete(lockKey);
            }

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    @Transactional
    public int markBookingScanned(String bookingId, String tripId) {
        return bookingRepository.markBookingScanned(bookingId, tripId);
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

    private Map<String, Object> createInvalidResponse(String message) {
        return createErrorResponse(400, message);
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}