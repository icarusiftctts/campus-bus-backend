package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Student;
import com.campusbus.repository.StudentRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.UUID;

/**
 * GOOGLE AUTH HANDLER
 * Handles Google OAuth authentication and student profile management
 * 
 * API: POST /auth/google
 * Body: { "email": "student@lnmiit.ac.in", "name": "John Doe", "googleToken": "..." }
 */
public class GoogleAuthHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static ConfigurableApplicationContext context;
    private static StudentRepository studentRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ALLOWED_DOMAIN = "@lnmiit.ac.in";

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

            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);

            String email = (String) body.get("email");
            String name = (String) body.get("name");

            if (!isValidCollegeEmail(email)) {
                return createErrorResponse(403, "Only @lnmiit.ac.in emails are allowed");
            }

            Student student = studentRepository.findByEmail(email);
            
            if (student != null) {
                String authToken = AuthTokenUtil.generateAuthToken(student.getStudentId(), email);
                boolean profileComplete = student.getRoom() != null && student.getPhone() != null;
                
                return createSuccessResponse(200, Map.of(
                    "studentId", student.getStudentId(),
                    "token", authToken,
                    "isNewUser", false,
                    "profileComplete", profileComplete,
                    "name", student.getName(),
                    "email", student.getEmail(),
                    "room", student.getRoom() != null ? student.getRoom() : "",
                    "phone", student.getPhone() != null ? student.getPhone() : ""
                ));
            } else {
                String studentId = "S" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Student newStudent = new Student();
                newStudent.setStudentId(studentId);
                newStudent.setEmail(email);
                newStudent.setName(name);
                
                studentRepository.save(newStudent);
                
                String authToken = AuthTokenUtil.generateAuthToken(studentId, email);
                
                return createSuccessResponse(201, Map.of(
                    "studentId", studentId,
                    "token", authToken,
                    "isNewUser", true,
                    "profileComplete", false,
                    "name", name,
                    "email", email
                ));
            }

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private boolean isValidCollegeEmail(String email) {
        return email != null && email.toLowerCase().endsWith(ALLOWED_DOMAIN);
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
