package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Booking;
import com.campusbus.repository.BookingRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.Optional;

/**
 * CANCEL BOOKING HANDLER
 * 
 * Purpose: Cancel bookings and automatically promote waitlisted students
 * 
 * API Gateway Integration:
 * - Method: DELETE
 * - Path: /api/bookings/{bookingId}
 * - Authorization: Required (JWT token)
 * 
 * Frontend Integration (Flutter):
 * 1. Make HTTP DELETE request to: https://your-api-gateway-url/api/bookings/B56EF78GH
 * 2. Headers: {
 *      "Content-Type": "application/json",
 *      "Authorization": "Bearer <authToken>"
 *    }
 * 3. No request body needed (bookingId in URL path)
 * 4. Success Response (200): {
 *      "message": "Booking cancelled and next waitlisted user promoted"
 *    }
 * 5. Error Response (403): {"message": "Not authorized to cancel this booking"}
 * 6. Error Response (400): {"message": "Booking already cancelled"}
 * 
 * Usage Flow:
 * - Student opens "My Bookings" screen in Flutter app
 * - Views list of active bookings with trip details
 * - Taps "Cancel" button on a booking
 * - Show confirmation dialog: "Are you sure you want to cancel?"
 * - If confirmed, Flutter sends DELETE request with bookingId
 * - On success: Remove booking from UI, show success message
 * - On error: Show error message
 * 
 * Cancellation Rules:
 * - Students can cancel their own bookings only
 * - Cannot cancel already cancelled bookings
 * - Cannot cancel bookings that are already scanned (boarded)
 * - Cancelling confirmed booking promotes next waitlisted student
 * - Cancelling waitlist booking just removes from waitlist
 * 
 * Waitlist Promotion:
 * - When confirmed booking is cancelled, next waitlisted student is promoted
 * - Promoted student gets QR code and push notification
 * - Waitlist positions are automatically updated for remaining students
 * - Real-time updates sent to all affected students
 * 
 * UI Considerations:
 * - Show cancellation deadline (e.g., "Cancel by 1 hour before departure")
 * - Disable cancel button after deadline or if already boarded
 * - Show penalty warning if applicable (future feature)
 * - Refresh booking list after successful cancellation
 */
public class CancelBookingHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static BookingRepository bookingRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            bookingRepository = context.getBean(BookingRepository.class);
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

            // Extract bookingId from path parameters
            Map<String, Object> pathParams = (Map<String, Object>) event.get("pathParameters");
            if (pathParams == null) {
                return createErrorResponse(400, "Missing bookingId in path");
            }
            String bookingId = (String) pathParams.get("bookingId");

            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isEmpty()) {
                return createErrorResponse(404, "Booking not found");
            }

            Booking booking = bookingOpt.get();
            
            // Verify ownership
            if (!booking.getStudentId().equals(studentId)) {
                return createErrorResponse(403, "Not authorized to cancel this booking");
            }

            // Check if already cancelled
            if ("CANCELLED".equals(booking.getStatus())) {
                return createErrorResponse(400, "Booking already cancelled");
            }

            // Check if already scanned (boarded)
            if ("SCANNED".equals(booking.getStatus())) {
                return createErrorResponse(400, "Cannot cancel booking after boarding");
            }

            String originalStatus = booking.getStatus();
            String tripId = booking.getTripId();

            // Cancel the booking
            booking.setStatus("CANCELLED");
            bookingRepository.save(booking);

            // If it was a confirmed booking, promote next waitlisted user
            if ("CONFIRMED".equals(originalStatus)) {
                Optional<Booking> nextWaitlisted = bookingRepository.findFirstWaitlistedBooking(tripId);
                if (nextWaitlisted.isPresent()) {
                    bookingRepository.promoteFromWaitlist(nextWaitlisted.get().getBookingId());
                    return createSuccessResponse(200, "Booking cancelled and next waitlisted user promoted");
                }
            }

            return createSuccessResponse(200, "Booking cancelled successfully");

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
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