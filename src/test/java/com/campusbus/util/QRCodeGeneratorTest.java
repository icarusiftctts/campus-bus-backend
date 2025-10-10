package com.campusbus.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QRCodeGeneratorTest {

    @Test
    void generateQRToken_ValidInputs_ReturnsToken() {
        // Arrange
        String bookingId = "B12345678";
        String tripId = "T12AB34CD";
        String studentId = "S12345678";

        // Act
        String token = QRCodeGenerator.generateQRToken(bookingId, tripId, studentId);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains(".")); // JWT format check
    }

    @Test
    void validateQRToken_ValidToken_ReturnsValidResult() {
        // Arrange
        String bookingId = "B12345678";
        String tripId = "T12AB34CD";
        String studentId = "S12345678";
        String token = QRCodeGenerator.generateQRToken(bookingId, tripId, studentId);

        // Act
        Map<String, Object> result = QRCodeGenerator.validateQRToken(token);

        // Assert
        assertTrue((Boolean) result.get("valid"));
        assertEquals(bookingId, result.get("bookingId"));
        assertEquals(tripId, result.get("tripId"));
        assertEquals(studentId, result.get("studentId"));
    }

    @Test
    void validateQRToken_InvalidToken_ReturnsInvalidResult() {
        // Arrange
        String invalidToken = "invalid.token.here";

        // Act
        Map<String, Object> result = QRCodeGenerator.validateQRToken(invalidToken);

        // Assert
        assertFalse((Boolean) result.get("valid"));
    }

    @Test
    void validateQRToken_NullToken_ReturnsInvalidResult() {
        // Act
        Map<String, Object> result = QRCodeGenerator.validateQRToken(null);

        // Assert
        assertFalse((Boolean) result.get("valid"));
    }

    @Test
    void validateQRToken_EmptyToken_ReturnsInvalidResult() {
        // Act
        Map<String, Object> result = QRCodeGenerator.validateQRToken("");

        // Assert
        assertFalse((Boolean) result.get("valid"));
    }

    @Test
    void generateAndValidate_RoundTrip_Success() {
        // Arrange
        String bookingId = "B87654321";
        String tripId = "T87654321";
        String studentId = "S87654321";

        // Act
        String token = QRCodeGenerator.generateQRToken(bookingId, tripId, studentId);
        Map<String, Object> result = QRCodeGenerator.validateQRToken(token);

        // Assert
        assertTrue((Boolean) result.get("valid"));
        assertEquals(bookingId, result.get("bookingId"));
        assertEquals(tripId, result.get("tripId"));
        assertEquals(studentId, result.get("studentId"));
    }
}