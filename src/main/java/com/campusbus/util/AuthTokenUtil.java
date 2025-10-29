package com.campusbus.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

public class AuthTokenUtil {
    
    private static final String AUTH_SECRET_KEY = System.getenv().getOrDefault("AUTH_SECRET_KEY", "campusbus-auth-secret-key-minimum-256-bits-required-for-hmac256-algorithm");
    private static final SecretKey key = Keys.hmacShaKeyFor(AUTH_SECRET_KEY.getBytes());
    
    public static String generateAuthToken(String studentId, String email) {
        Instant now = Instant.now();
        Instant expiration = now.plus(7, ChronoUnit.DAYS); // Auth token valid for 7 days
        
        return Jwts.builder()
                .subject(studentId)
                .claim("email", email)
                .claim("type", "auth")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }
    
    public static Map<String, Object> validateAuthToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Verify this is an auth token
            if (!"auth".equals(claims.get("type", String.class))) {
                return Map.of("valid", false, "error", "Invalid token type");
            }
            
            return Map.of(
                "studentId", claims.getSubject(),
                "email", claims.get("email", String.class),
                "valid", true
            );
        } catch (Exception e) {
            return Map.of("valid", false, "error", e.getMessage());
        }
    }
    
    public static String extractStudentId(String token) {
        Map<String, Object> tokenData = validateAuthToken(token);
        if ((Boolean) tokenData.get("valid")) {
            return (String) tokenData.get("studentId");
        }
        return null;
    }
    
    /**
     * Generate JWT token for operator authentication.
     * Used by OperatorLoginHandler after successful password verification.
     * 
     * Token includes:
     * - Subject: operatorId
     * - Claim: employeeId (for display purposes)
     * - Claim: role = "OPERATOR" (distinguishes from student tokens)
     * - Claim: type = "operator" (for token validation)
     * - Expiry: 24 hours (longer than student tokens for shift work)
     * 
     * @param operatorId The operator's unique identifier
     * @param employeeId The operator's employee ID
     * @return JWT token string
     */
    public static String generateOperatorToken(String operatorId, String employeeId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(24, ChronoUnit.HOURS); // Operator token valid for 24 hours
        
        return Jwts.builder()
                .subject(operatorId)
                .claim("employeeId", employeeId)
                .claim("role", "OPERATOR")
                .claim("type", "operator")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }
    
    /**
     * Validate operator JWT token.
     * Used by all operator Lambda handlers for authentication.
     * 
     * Validates:
     * - Token signature and expiration
     * - Token type is "operator"
     * - Role is "OPERATOR"
     * 
     * @param token The JWT token to validate
     * @return Map containing validation result and claims
     */
    public static Map<String, Object> validateOperatorToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            // Verify this is an operator token
            if (!"operator".equals(claims.get("type", String.class))) {
                return Map.of("valid", false, "error", "Invalid token type");
            }
            
            // Verify role is OPERATOR
            if (!"OPERATOR".equals(claims.get("role", String.class))) {
                return Map.of("valid", false, "error", "Invalid role");
            }
            
            return Map.of(
                "operatorId", claims.getSubject(),
                "employeeId", claims.get("employeeId", String.class),
                "role", claims.get("role", String.class),
                "valid", true
            );
        } catch (Exception e) {
            return Map.of("valid", false, "error", e.getMessage());
        }
    }
    
    /**
     * Extract operator ID from token.
     * Convenience method for getting operator ID from validated token.
     * 
     * @param token The JWT token
     * @return Operator ID or null if invalid
     */
    public static String extractOperatorId(String token) {
        Map<String, Object> tokenData = validateOperatorToken(token);
        if ((Boolean) tokenData.get("valid")) {
            return (String) tokenData.get("operatorId");
        }
        return null;
    }
}