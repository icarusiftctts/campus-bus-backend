package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.repository.BookingRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;

/**
 * GET PASSENGER LIST HANDLER
 * 
 * Purpose: Retrieve list of booked passengers for a trip
 * 
 * API Gateway Integration:
 * - Method: GET
 * - Path: /operator/trips/{tripId}/passengers
 * - Authorization: Required (Operator JWT token)
 * - Headers: Authorization: Bearer {token}
 * 
 * Path Parameters:
 * - tripId: The trip identifier (e.g., "T401")
 * 
 * Success Response (200):
 * {
 *   "tripId": "T401",
 *   "passengers": [
 *     {
 *       "id": "STU101",
 *       "name": "Alice Johnson",
 *       "status": "SCANNED",
 *       "boardingStatus": "Boarded"
 *     },
 *     {
 *       "id": "STU102",
 *       "name": "Bob Williams",
 *       "status": "CONFIRMED",
 *       "boardingStatus": "Not Boarded"
 *     }
 *   ],
 *   "totalCount": 25
 * }
 * 
 * Error Responses:
 * - 401: Missing or invalid authorization header
 * - 401: Invalid token
 * - 400: Missing tripId parameter
 * - 500: Server error
 * 
 * Frontend Integration (Mobile Operator App):
 * - PassengerListScreen calls this on screen load
 * - Shows list of passengers with boarding status
 * - Allows operator to tap passenger for misconduct reporting
 * - Real-time updates when QR codes are scanned
 * 
 * Passenger Status Logic:
 * - SCANNED: QR code validated, passenger boarded
 * - CONFIRMED: Booking confirmed, QR not yet scanned
 * - boardingStatus: Derived field ("Boarded" or "Not Boarded")
 * 
 * Database Queries:
 * - Uses BookingRepository.findPassengersByTripId() with JOIN to students
 * - Filters by tripId and status IN ('CONFIRMED', 'SCANNED')
 * - Orders by student name for easy scanning
 * 
 * Security:
 * - Only operators can access passenger lists
 * - Token validation ensures authorized access
 * - No sensitive student data exposed (only ID, name, status)
 * 
 * AWS Configuration:
 * - Lambda execution role needs RDS access
 * - Environment variables: DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD
 * - CloudWatch logs: /aws/lambda/GetPassengerListHandler
 */
public class GetPassengerListHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static BookingRepository bookingRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initialize Spring context and repositories.
     * Called once per Lambda container lifecycle.
     */
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

            // Extract and validate authorization header
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return createErrorResponse(401, "Missing or invalid authorization header");
            }

            // Validate operator token
            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateOperatorToken(token);
            
            if (!(Boolean) tokenData.get("valid")) {
                return createErrorResponse(401, "Invalid token");
            }

            // Extract tripId from path parameters
            @SuppressWarnings("unchecked")
            Map<String, String> pathParams = (Map<String, String>) event.get("pathParameters");
            if (pathParams == null) {
                return createErrorResponse(400, "Missing path parameters");
            }
            
            String tripId = pathParams.get("tripId");
            if (tripId == null || tripId.trim().isEmpty()) {
                return createErrorResponse(400, "Missing tripId parameter");
            }

            // Get passenger list for the trip
            List<Map<String, Object>> passengers = bookingRepository.findPassengersByTripId(tripId.trim());

            // Prepare success response
            Map<String, Object> responseData = Map.of(
                "tripId", tripId.trim(),
                "passengers", passengers,
                "totalCount", passengers.size()
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            // Log error for debugging
            System.err.println("GetPassengerListHandler error: " + e.getMessage());
            e.printStackTrace();
            
            return createErrorResponse(500, "Internal server error. Please try again later.");
        }
    }

    /**
     * Create success response with data.
     * 
     * @param statusCode HTTP status code
     * @param data Response data
     * @return API Gateway response format
     */
    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*",
                    "Access-Control-Allow-Headers", "Content-Type,Authorization",
                    "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"
                ),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    /**
     * Create error response with message.
     * 
     * @param statusCode HTTP status code
     * @param message Error message
     * @return API Gateway response format
     */
    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Headers", "Content-Type,Authorization",
                "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"
            ),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}
