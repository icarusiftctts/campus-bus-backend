package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.TripAssignment;
import com.campusbus.repository.TripAssignmentRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * START TRIP HANDLER
 * 
 * Purpose: Mark trip as started and create assignment
 * 
 * API Gateway Integration:
 * - Method: POST
 * - Path: /operator/trips/start
 * - Authorization: Required (Operator JWT token)
 * - Headers: Authorization: Bearer {token}
 * - Content-Type: application/json
 * 
 * Request Body:
 * {
 *   "tripId": "T401",
 *   "busNumber": "Bus #05"
 * }
 * 
 * Success Response (200):
 * {
 *   "assignmentId": "TA12AB34",
 *   "tripId": "T401",
 *   "status": "IN_PROGRESS",
 *   "message": "Trip started successfully"
 * }
 * 
 * Error Responses:
 * - 400: Missing tripId or busNumber
 * - 401: Missing or invalid authorization header
 * - 401: Invalid token
 * - 409: Trip already has active assignment
 * - 500: Server error
 * 
 * Frontend Integration (Mobile Operator App):
 * - BusSelectionScreen calls this when operator confirms trip selection
 * - Creates trip assignment with IN_PROGRESS status
 * - Navigates to ScannerScreen with tripId
 * - Starts GPS tracking service
 * 
 * Business Rules:
 * - Only one active assignment per trip at a time
 * - Assignment ID auto-generated with "TA" prefix
 * - Started timestamp recorded for trip duration tracking
 * - Bus number required for assignment
 * 
 * Database Operations:
 * - Creates new TripAssignment record
 * - Sets status to IN_PROGRESS
 * - Records started_at timestamp
 * - Links operator to trip
 * 
 * AWS Configuration:
 * - Lambda execution role needs RDS access
 * - Environment variables: DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD
 * - CloudWatch logs: /aws/lambda/StartTripHandler
 */
public class StartTripHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static TripAssignmentRepository assignmentRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initialize Spring context and repositories.
     * Called once per Lambda container lifecycle.
     */
    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            assignmentRepository = context.getBean(TripAssignmentRepository.class);
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

            // Parse request body
            String requestBody = (String) event.get("body");
            if (requestBody == null || requestBody.trim().isEmpty()) {
                return createErrorResponse(400, "Missing request body");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);
            String tripId = (String) body.get("tripId");
            String busNumber = (String) body.get("busNumber");

            // Validate required fields
            if (tripId == null || tripId.trim().isEmpty()) {
                return createErrorResponse(400, "Missing tripId");
            }
            if (busNumber == null || busNumber.trim().isEmpty()) {
                return createErrorResponse(400, "Missing busNumber");
            }

            // Check if trip already has active assignment
            var existingAssignment = assignmentRepository.findActiveAssignmentByTripId(tripId.trim());
            if (existingAssignment.isPresent()) {
                return createErrorResponse(409, "Trip already has an active assignment");
            }

            // Create new trip assignment
            String assignmentId = "TA" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            
            TripAssignment assignment = new TripAssignment();
            assignment.setAssignmentId(assignmentId);
            assignment.setTripId(tripId.trim());
            assignment.setOperatorId(operatorId);
            assignment.setBusNumber(busNumber.trim());
            assignment.setStartedAt(LocalDateTime.now());
            assignment.setStatus("IN_PROGRESS");

            // Save assignment
            assignmentRepository.save(assignment);

            // Prepare success response
            Map<String, Object> responseData = Map.of(
                "assignmentId", assignmentId,
                "tripId", tripId.trim(),
                "status", "IN_PROGRESS",
                "message", "Trip started successfully"
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            // Log error for debugging
            System.err.println("StartTripHandler error: " + e.getMessage());
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
