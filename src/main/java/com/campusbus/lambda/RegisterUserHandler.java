package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Student;
import com.campusbus.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.UUID;

/**
 * REGISTER USER HANDLER
 * 
 * Purpose: Register new students with email/password authentication
 * 
 * API Gateway Integration:
 * - Method: POST
 * - Path: /api/auth/register
 * - Authorization: None (public endpoint)
 * 
 * Frontend Integration (Flutter):
 * 1. Make HTTP POST request to: https://your-api-gateway-url/api/auth/register
 * 2. Headers: {"Content-Type": "application/json"}
 * 3. Body: {
 *      "email": "student@college.edu",
 *      "name": "John Doe", 
 *      "password": "securePassword123",
 *      "room": "A-101",
 *      "phone": "+91-9876543210"
 *    }
 * 4. Success Response (201): {"message": "Student registered: S12AB34CD"}
 * 5. Error Response (400): {"message": "Email already registered"}
 * 
 * Usage Flow:
 * - Student opens registration screen in Flutter app
 * - Enters college email, name, password, room, phone
 * - Flutter validates email format (must be college domain)
 * - Sends POST request to this endpoint
 * - On success: Navigate to login screen
 * - On error: Show error message to user
 */
public class RegisterUserHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

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
            String name = (String) body.get("name");
            String password = (String) body.get("password");
            String room = (String) body.get("room");
            String phone = (String) body.get("phone");

            if (studentRepository.existsByEmail(email)) {
                return createResponse(400, "Email already registered");
            }

            String studentId = "S" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String hashedPassword = passwordEncoder.encode(password);
            
            Student student = new Student(studentId, email, name, hashedPassword, room, phone);
            studentRepository.save(student);

            return createResponse(201, "Student registered: " + studentId);

        } catch (Exception e) {
            return createResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}