package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Operator;
import com.campusbus.repository.OperatorRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OPERATOR LOGIN HANDLER
 * 
 * Purpose: Authenticate bus operators using employee ID and password
 * 
 * API Gateway Integration:
 * - Method: POST
 * - Path: /operator/login
 * - Authorization: None (public endpoint)
 * - Content-Type: application/json
 * 
 * Request Body:
 * {
 *   "employeeId": "op101",
 *   "password": "buspass"
 * }
 * 
 * Success Response (200):
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "operatorId": "OP001",
 *   "employeeId": "op101",
 *   "name": "Rajesh Kumar",
 *   "message": "Login successful"
 * }
 * 
 * Error Responses:
 * - 400: Missing employeeId or password
 * - 401: Invalid credentials
 * - 403: Account suspended/inactive
 * - 500: Server error
 * 
 * Frontend Integration (Mobile Operator App):
 * - OperatorLoginScreen sends POST request with credentials
 * - On success: Store token in AsyncStorage, navigate to BusSelectionScreen
 * - On error: Show error message, clear password field
 * 
 * Security Features:
 * - BCrypt password hashing (cost factor 10)
 * - Employee ID case-sensitive lookup
 * - Account status validation (ACTIVE required)
 * - JWT token with 24-hour expiry
 * - Last login timestamp tracking
 * 
 * Database Requirements:
 * - operators table with password_hash field
 * - Sample operator: employeeId='op101', password='buspass'
 * - Password hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 * 
 * AWS Configuration:
 * - Lambda execution role needs RDS access
 * - Environment variables: DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD
 * - CloudWatch logs: /aws/lambda/OperatorLoginHandler
 */
public class OperatorLoginHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static OperatorRepository operatorRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Initialize Spring context and repositories.
     * Called once per Lambda container lifecycle.
     */
    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            operatorRepository = context.getBean(OperatorRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            // Parse request body
            String requestBody = (String) event.get("body");
            if (requestBody == null || requestBody.trim().isEmpty()) {
                return createErrorResponse(400, "Missing request body");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);
            String employeeId = (String) body.get("employeeId");
            String password = (String) body.get("password");

            // Validate required fields
            if (employeeId == null || employeeId.trim().isEmpty()) {
                return createErrorResponse(400, "Missing employeeId");
            }
            if (password == null || password.trim().isEmpty()) {
                return createErrorResponse(400, "Missing password");
            }

            // Find operator by employee ID
            Operator operator = operatorRepository.findByEmployeeId(employeeId.trim());
            if (operator == null) {
                return createErrorResponse(401, "Invalid credentials");
            }

            // Check operator status
            if (!operator.isActive()) {
                String statusMessage = operator.isSuspended() ? "suspended" : "inactive";
                return createErrorResponse(403, "Operator account is " + statusMessage + ". Contact administrator.");
            }

            // Verify password using BCrypt
            if (!passwordEncoder.matches(password, operator.getPasswordHash())) {
                return createErrorResponse(401, "Invalid credentials");
            }

            // Update last login timestamp
            operator.setLastLogin(LocalDateTime.now());
            operatorRepository.save(operator);

            // Generate JWT token
            String authToken = AuthTokenUtil.generateOperatorToken(operator.getOperatorId(), operator.getEmployeeId());

            // Prepare success response
            Map<String, Object> responseData = Map.of(
                "token", authToken,
                "operatorId", operator.getOperatorId(),
                "employeeId", operator.getEmployeeId(),
                "name", operator.getName(),
                "message", "Login successful"
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            // Log error for debugging
            System.err.println("OperatorLoginHandler error: " + e.getMessage());
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
