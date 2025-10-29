package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Student;
import com.campusbus.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

/**
 * COMPLETE PROFILE HANDLER
 * Updates student profile with room and phone after Google OAuth
 * 
 * API: PUT /auth/complete-profile
 * Body: { "studentId": "S12AB34CD", "room": "A-101", "phone": "+91-9876543210" }
 */
public class CompleteProfileHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static ConfigurableApplicationContext context;
    private static StudentRepository studentRepository;
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

            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);

            String studentId = (String) body.get("studentId");
            String room = (String) body.get("room");
            String phone = (String) body.get("phone");

            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                return createErrorResponse(404, "Student not found");
            }

            student.setRoom(room);
            student.setPhone(phone);
            studentRepository.save(student);

            return createSuccessResponse(200, Map.of(
                "message", "Profile completed successfully",
                "profileComplete", true
            ));

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
