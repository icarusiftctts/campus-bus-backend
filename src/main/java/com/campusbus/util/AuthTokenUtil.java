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
}