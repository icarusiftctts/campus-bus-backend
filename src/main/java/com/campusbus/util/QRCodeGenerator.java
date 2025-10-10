package com.campusbus.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

public class QRCodeGenerator {
    
    private static final String SECRET_KEY = System.getenv().getOrDefault("QR_SECRET_KEY", "campusbus-qr-secret-key-minimum-256-bits-required-for-hmac256-algorithm");
    private static final SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    
    public static String generateQRToken(String bookingId, String tripId, String studentId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(24, ChronoUnit.HOURS); // QR valid for 24 hours
        
        return Jwts.builder()
                .subject(bookingId)
                .claim("tripId", tripId)
                .claim("studentId", studentId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }
    
    public static Map<String, Object> validateQRToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return Map.of(
                "bookingId", claims.getSubject(),
                "tripId", claims.get("tripId", String.class),
                "studentId", claims.get("studentId", String.class),
                "valid", true
            );
        } catch (Exception e) {
            return Map.of("valid", false, "error", e.getMessage());
        }
    }
}