package com.campusbus.lambda;

import com.campusbus.entity.Booking;
import com.campusbus.repository.BookingRepository;
import com.campusbus.util.QRCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class ValidateQRHandlerTest {

    private ValidateQRHandler handler;
    private BookingRepository bookingRepository;

    @BeforeEach
    void setUp() {
        handler = new ValidateQRHandler();
        bookingRepository = mock(BookingRepository.class);
    }

    @Test
    void validateQR_ValidToken_MarksScanned() {
        // Arrange
        Map<String, Object> event = Map.of(
            "body", "{\"qrToken\":\"valid-qr-token\"}"
        );
        
        Booking booking = new Booking("B12345678", "S12345678", "T12AB34CD", "CONFIRMED");
        booking.setQrToken("valid-qr-token");

        try (MockedStatic<QRCodeGenerator> qrMock = mockStatic(QRCodeGenerator.class)) {
            qrMock.when(() -> QRCodeGenerator.validateQRToken("valid-qr-token"))
                    .thenReturn(Map.of("valid", true, "bookingId", "B12345678", "tripId", "T12AB34CD"));
            
            when(bookingRepository.findById("B12345678")).thenReturn(Optional.of(booking));
            when(bookingRepository.markAsScanned("B12345678")).thenReturn(1);

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(200, response.get("statusCode"));
            verify(bookingRepository).markAsScanned("B12345678");
        }
    }

    @Test
    void validateQR_InvalidToken_ReturnsError() {
        // Arrange
        Map<String, Object> event = Map.of(
            "body", "{\"qrToken\":\"invalid-token\"}"
        );

        try (MockedStatic<QRCodeGenerator> qrMock = mockStatic(QRCodeGenerator.class)) {
            qrMock.when(() -> QRCodeGenerator.validateQRToken("invalid-token"))
                    .thenReturn(Map.of("valid", false));

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(400, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("Invalid QR"));
        }
    }

    @Test
    void validateQR_BookingNotFound_ReturnsError() {
        // Arrange
        Map<String, Object> event = Map.of(
            "body", "{\"qrToken\":\"valid-token\"}"
        );

        try (MockedStatic<QRCodeGenerator> qrMock = mockStatic(QRCodeGenerator.class)) {
            qrMock.when(() -> QRCodeGenerator.validateQRToken("valid-token"))
                    .thenReturn(Map.of("valid", true, "bookingId", "B12345678", "tripId", "T12AB34CD"));
            
            when(bookingRepository.findById("B12345678")).thenReturn(Optional.empty());

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(404, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("Booking not found"));
        }
    }

    @Test
    void validateQR_AlreadyScanned_ReturnsError() {
        // Arrange
        Map<String, Object> event = Map.of(
            "body", "{\"qrToken\":\"valid-token\"}"
        );
        
        Booking scannedBooking = new Booking("B12345678", "S12345678", "T12AB34CD", "SCANNED");

        try (MockedStatic<QRCodeGenerator> qrMock = mockStatic(QRCodeGenerator.class)) {
            qrMock.when(() -> QRCodeGenerator.validateQRToken("valid-token"))
                    .thenReturn(Map.of("valid", true, "bookingId", "B12345678", "tripId", "T12AB34CD"));
            
            when(bookingRepository.findById("B12345678")).thenReturn(Optional.of(scannedBooking));

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(400, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("already scanned"));
        }
    }
}