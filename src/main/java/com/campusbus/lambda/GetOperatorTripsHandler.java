package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.repository.TripRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * GET OPERATOR TRIPS HANDLER
 * 
 * Purpose: Retrieve available and assigned trips for operator
 * 
 * API Gateway Integration:
 * - Method: GET
 * - Path: /operator/trips
 * - Authorization: Required (Operator JWT token)
 * - Headers: Authorization: Bearer {token}
 * 
 * Success Response (200):
 * {
 *   "trips": [
 *     {
 *       "id": "T401",
 *       "time": "18:30:00",
 *       "busNumber": "Bus #05",
 *       "route": "Campus → City",
 *       "status": "Upcoming"
 *     },
 *     {
 *       "id": "T402",
 *       "time": "19:00:00",
 *       "busNumber": "Bus #12",
 *       "route": "City → Campus",
 *       "status": "Active"
 *     }
 *   ],
 *   "date": "2024-01-15"
 * }
 * 
 * Error Responses:
 * - 401: Missing or invalid authorization header
 * - 401: Invalid token
 * - 500: Server error
 * 
 * Frontend Integration (Mobile Operator App):
 * - BusSelectionScreen calls this on screen load
 * - Shows trips with status: Active, Upcoming, Completed
 * - Operator selects trip to start scanning
 * - Real-time updates when trip status changes
 * 
 * Trip Status Logic:
 * - Active: Assignment status is IN_PROGRESS
 * - Upcoming: Assignment not started, departure time in future
 * - Completed: Assignment completed or departure time passed
 * 
 * Database Queries:
 * - Uses TripRepository.findOperatorTrips() with JOIN to trip_assignments
 * - Filters by operator ID and current date
 * - Orders by departure time
 * 
 * AWS Configuration:
 * - Lambda execution role needs RDS access
 * - Environment variables: DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD
 * - CloudWatch logs: /aws/lambda/GetOperatorTripsHandler
 */
public class GetOperatorTripsHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static TripRepository tripRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initialize Spring context and repositories.
     * Called once per Lambda container lifecycle.
     */
    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            tripRepository = context.getBean(TripRepository.class);
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

            String operatorId = (String) tokenData.get("operatorId");

            // Get trips for today
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> trips = tripRepository.findOperatorTrips(operatorId, today);

            // Prepare success response
            Map<String, Object> responseData = Map.of(
                "trips", trips,
                "date", today.toString()
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            // Log error for debugging
            System.err.println("GetOperatorTripsHandler error: " + e.getMessage());
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
