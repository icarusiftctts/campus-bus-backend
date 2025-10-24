// amazonq-ignore-next-line
package com.campusbus.lambda;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import com.campusbus.entity.Student;
import com.campusbus.repository.StudentRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.UUID;

import com.campusbus.util.AuthTokenUtil;

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
            String room = (String) body.get("room");
            String phone = (String) body.get("phone");
            String password = (String) body.get("password");

            // Check if student already exists in DB
            if (studentRepository.existsByEmail(email)) {
                return createErrorResponse(400, "Email already registered");
            }

            // Create user in AWS Cognito first
            String cognitoUserId = createUserInCognito(email, password, name);
            if (cognitoUserId == null) {
                return createErrorResponse(400, "Failed to create user in Cognito");
            }

            // Create student record in database (without password)
            String studentId = "S" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Student student = new Student();
            student.setStudentId(studentId);
            student.setEmail(email);
            student.setName(name);
            student.setRoom(room);
            student.setPhone(phone);
            // No password stored in DB - handled by Cognito

            studentRepository.save(student);

            String authToken = AuthTokenUtil.generateAuthToken(studentId, email);

            Map<String, Object> responseData = Map.of(
                "studentId", studentId,
                "token", authToken,
                "message", "Student registered successfully"
            );
            return createSuccessResponse(201, responseData);

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

    private String createUserInCognito(String email, String password, String name) {
        try {
            AWSCognitoIdentityProvider cognitoClient = AWSCognitoIdentityProviderClientBuilder.defaultClient();
            
            AdminCreateUserRequest request = new AdminCreateUserRequest()
                    .withUserPoolId(System.getenv("COGNITO_USER_POOL_ID"))
                    .withUsername(email)
                    .withTemporaryPassword(password)
                    .withMessageAction(MessageActionType.SUPPRESS) // Don't send welcome email
                    .withUserAttributes(
                            new AttributeType().withName("email").withValue(email),
                            new AttributeType().withName("name").withValue(name),
                            new AttributeType().withName("email_verified").withValue("true")
                    );

            AdminCreateUserResult result = cognitoClient.adminCreateUser(request);
            
            // Set permanent password
            AdminSetUserPasswordRequest passwordRequest = new AdminSetUserPasswordRequest()
                    .withUserPoolId(System.getenv("COGNITO_USER_POOL_ID"))
                    .withUsername(email)
                    .withPassword(password)
                    .withPermanent(true);
            
            cognitoClient.adminSetUserPassword(passwordRequest);
            
            return result.getUser().getUsername();
        } catch (Exception e) {
            System.err.println("Cognito user creation failed: " + e.getMessage());
            return null;
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