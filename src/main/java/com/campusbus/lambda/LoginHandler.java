package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Student;
import com.campusbus.repository.StudentRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

/**
 * LOGIN HANDLER
 * 
 * Purpose: Authenticate students and provide JWT auth tokens for API access
 * 
 * API Gateway Integration:
 * - Method: POST
 * - Path: /api/auth/login
 * - Authorization: None (public endpoint)
 * 
 * Frontend Integration (Flutter):
 * 1. Make HTTP POST request to: https://your-api-gateway-url/api/auth/login
 * 2. Headers: {"Content-Type": "application/json"}
 * 3. Body: {
 *      "email": "student@college.edu",
 *      "password": "securePassword123"
 *    }
 * 4. Success Response (200): {
 *      "authToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *      "studentId": "S12AB34CD",
 *      "name": "John Doe",
 *      "email": "student@college.edu",
 *      "room": "A-101",
 *      "phone": "+91-9876543210",
 *      "penaltyCount": 0,
 *      "message": "Login successful"
 *    }
 * 5. Error Response (401): {"message": "Invalid email or password"}
 * 
 * Usage Flow:
 * - Student opens login screen in Flutter app
 * - Enters email and password
 * - Flutter sends POST request to this endpoint
 * - On success: Store authToken in secure storage, navigate to home screen
 * - On error: Show error message
 * - Use authToken in Authorization header for all subsequent API calls
 * 
 * Token Usage:
 * - Store authToken securely (Flutter Secure Storage)
 * - Include in all API calls: Authorization: Bearer <authToken>
 * - Token expires in 7 days, then user needs to login again
 */
public class LoginHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static StudentRepository studentRepository;
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            studentRepository = context.getBean(StudentRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            // Parse API Gateway request body
            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);

            String email = (String) body.get("email");
            String password = (String) body.get("password");

            // Find student by email
            Optional<Student> studentOpt = studentRepository.findByEmail(email);
            if (studentOpt.isEmpty()) {
                return createErrorResponse(401, "Invalid email or password");
            }

            Student student = studentOpt.get();

            // Verify password
            if (!passwordEncoder.matches(password, student.getPassword())) {
                return createErrorResponse(401, "Invalid email or password");
            }

            // Generate auth token
            String authToken = AuthTokenUtil.generateAuthToken(student.getStudentId(), student.getEmail());

            Map<String, Object> responseData = Map.of(
                "authToken", authToken,
                "studentId", student.getStudentId(),
                "name", student.getName(),
                "email", student.getEmail(),
                "room", student.getRoom(),
                "phone", student.getPhone(),
                "penaltyCount", student.getPenaltyCount(),
                "message", "Login successful"
            );

            return createSuccessResponse(200, responseData);

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