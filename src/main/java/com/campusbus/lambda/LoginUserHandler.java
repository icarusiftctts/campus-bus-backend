package com.campusbus.lambda;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.Optional;

import com.campusbus.repository.StudentRepository;
import com.campusbus.entity.Student;

public class LoginUserHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static AWSCognitoIdentityProvider cognitoClient;

    private static StudentRepository studentRepository;

    static {
        cognitoClient = AWSCognitoIdentityProviderClientBuilder.defaultClient();
    }

    private static ConfigurableApplicationContext context;
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

            String cognitoToken = loginUserInCognito(email, password);
            if (cognitoToken != null) {

                Optional<Student> studentOpt = studentRepository.findByEmail(email);
                if (studentOpt.isEmpty()) {
                    return createErrorResponse(404, "Student not found in database");
                }

                Student student = studentOpt.get();

                // Generate our own auth token for API access
                String authToken = AuthTokenUtil.generateAuthToken(student.getStudentId(), email);
                
                Map<String, Object> responseData = Map.of(
                        "token", authToken,
                        "studentId", student.getStudentId(),
                        "email", student.getEmail(),
                        "name", student.getName(),
                        "message", "Login successful"
                );
                return createSuccessResponse(200, responseData);
            } else {
                return createErrorResponse(401, "Invalid credentials");
            }

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private String loginUserInCognito(String email, String password) {
        try {
            AdminInitiateAuthRequest request = new AdminInitiateAuthRequest()
                    // amazonq-ignore-next-line
                    .withUserPoolId(System.getenv("COGNITO_USER_POOL_ID"))
                    .withClientId(System.getenv("COGNITO_CLIENT_ID"))
                    .withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                    .withAuthParameters(
                            new java.util.HashMap<String, String>() {{
                                put("USERNAME", email);
                                put("PASSWORD", password);
                            }}
                    );

            AdminInitiateAuthResult result = cognitoClient.adminInitiateAuth(request);
            return result.getAuthenticationResult().getIdToken(); // JWT token

        } catch (Exception e) {
            throw new RuntimeException("Cognito login failed: " + e.getMessage());
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
