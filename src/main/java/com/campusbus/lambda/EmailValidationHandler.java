package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class EmailValidationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String COLLEGE_DOMAIN = "@lnmiit.ac.in"; // Replace with your domain
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            // Extract user attributes from Cognito event
            JsonNode request = objectMapper.readTree((String) event.get("request"));
            JsonNode userAttributes = request.get("userAttributes");

            String email = userAttributes.get("email").asText();

            // Validate college email domain
            if (!email.toLowerCase().endsWith(COLLEGE_DOMAIN.toLowerCase())) {
                throw new RuntimeException("Only " + COLLEGE_DOMAIN + " emails are allowed");
            }

            // If validation passes, return success response
            Map<String, Object> response = Map.of(
                    "autoConfirmUser", true, // Auto-confirm email (optional)
                    "autoVerifyEmail", true  // Auto-verify email (optional)
            );

            return Map.of(
                    "response", response
            );

        } catch (Exception e) {
            // If validation fails, Cognito will reject the registration/login
            context.getLogger().log("Email validation failed: " + e.getMessage());
            throw new RuntimeException("Email validation failed: " + e.getMessage());
        }
    }
}