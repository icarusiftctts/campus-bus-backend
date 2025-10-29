package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

/**
 * COGNITO PRE-SIGNUP TRIGGER
 * Validates email domain before allowing Cognito user creation
 * Blocks any email that doesn't end with @lnmiit.ac.in
 */
public class CognitoPreSignupHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    
    private static final String ALLOWED_DOMAIN = "@lnmiit.ac.in";
    
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            Map<String, Object> request = (Map<String, Object>) event.get("request");
            Map<String, Object> userAttributes = (Map<String, Object>) request.get("userAttributes");
            String email = (String) userAttributes.get("email");
            
            if (email == null || !email.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
                throw new RuntimeException("Only " + ALLOWED_DOMAIN + " emails are allowed");
            }
            
            event.put("response", Map.of(
                "autoConfirmUser", true,
                "autoVerifyEmail", true
            ));
            
            return event;
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Email validation failed: " + e.getMessage());
        }
    }
}
