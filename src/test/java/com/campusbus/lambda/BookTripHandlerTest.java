package com.campusbus.lambda;

import com.campusbus.entity.Booking;
import com.campusbus.entity.Student;
import com.campusbus.entity.Trip;
import com.campusbus.repository.BookingRepository;
import com.campusbus.repository.StudentRepository;
import com.campusbus.repository.TripRepository;
import com.campusbus.util.AuthTokenUtil;
import com.campusbus.util.QRCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class BookTripHandlerTest {

    private BookTripHandler handler;
    private StudentRepository studentRepository;
    private TripRepository tripRepository;
    private BookingRepository bookingRepository;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        handler = new BookTripHandler();
        studentRepository = mock(StudentRepository.class);
        tripRepository = mock(TripRepository.class);
        bookingRepository = mock(BookingRepository.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void bookTrip_StudentBlocked_ReturnsError() {
        // Arrange
        Map<String, Object> event = createValidEvent();
        Student blockedStudent = new Student("S12345678", "test@college.edu", "Test Student", "password", "A101", "9876543210");
        blockedStudent.setPenaltyCount(3);
        blockedStudent.setBlockedUntil(LocalDateTime.now().plusDays(1));

        try (MockedStatic<AuthTokenUtil> authMock = mockStatic(AuthTokenUtil.class)) {
            authMock.when(() -> AuthTokenUtil.validateAuthToken(anyString()))
                    .thenReturn(Map.of("valid", true, "studentId", "S12345678"));
            
            when(studentRepository.findById("S12345678")).thenReturn(Optional.of(blockedStudent));

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(403, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("blocked due to penalties"));
        }
    }

    @Test
    void bookTrip_MissingRequestBody_ReturnsError() {
        // Arrange
        Map<String, Object> event = Map.of(
            "headers", Map.of("Authorization", "Bearer validtoken")
        );

        try (MockedStatic<AuthTokenUtil> authMock = mockStatic(AuthTokenUtil.class)) {
            authMock.when(() -> AuthTokenUtil.validateAuthToken(anyString()))
                    .thenReturn(Map.of("valid", true, "studentId", "S12345678"));

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(400, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("Missing request body"));
        }
    }

    @Test
    void bookTrip_TripNotFound_ReturnsError() {
        // Arrange
        Map<String, Object> event = createValidEvent();
        Student student = createValidStudent();

        try (MockedStatic<AuthTokenUtil> authMock = mockStatic(AuthTokenUtil.class)) {
            authMock.when(() -> AuthTokenUtil.validateAuthToken(anyString()))
                    .thenReturn(Map.of("valid", true, "studentId", "S12345678"));
            
            when(studentRepository.findById("S12345678")).thenReturn(Optional.of(student));
            when(tripRepository.findById("T12AB34CD")).thenReturn(Optional.empty());

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(404, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("Trip not found"));
        }
    }

    @Test
    void bookTrip_AlreadyBooked_ReturnsError() {
        // Arrange
        Map<String, Object> event = createValidEvent();
        Student student = createValidStudent();
        Trip trip = createValidTrip();
        Booking existingBooking = new Booking("B12345678", "S12345678", "T12AB34CD", "CONFIRMED");

        try (MockedStatic<AuthTokenUtil> authMock = mockStatic(AuthTokenUtil.class)) {
            authMock.when(() -> AuthTokenUtil.validateAuthToken(anyString()))
                    .thenReturn(Map.of("valid", true, "studentId", "S12345678"));
            
            when(studentRepository.findById("S12345678")).thenReturn(Optional.of(student));
            when(tripRepository.findById("T12AB34CD")).thenReturn(Optional.of(trip));
            when(bookingRepository.findByStudentIdAndTripId("S12345678", "T12AB34CD"))
                    .thenReturn(Optional.of(existingBooking));

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(400, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("Already booked"));
        }
    }

    @Test
    void bookTrip_AvailableSeats_ConfirmsBooking() {
        // Arrange
        Map<String, Object> event = createValidEvent();
        Student student = createValidStudent();
        Trip trip = createValidTrip();

        try (MockedStatic<AuthTokenUtil> authMock = mockStatic(AuthTokenUtil.class);
             MockedStatic<QRCodeGenerator> qrMock = mockStatic(QRCodeGenerator.class)) {
            
            authMock.when(() -> AuthTokenUtil.validateAuthToken(anyString()))
                    .thenReturn(Map.of("valid", true, "studentId", "S12345678"));
            qrMock.when(() -> QRCodeGenerator.generateQRToken(anyString(), anyString(), anyString()))
                    .thenReturn("mock-qr-token");
            
            when(studentRepository.findById("S12345678")).thenReturn(Optional.of(student));
            when(tripRepository.findById("T12AB34CD")).thenReturn(Optional.of(trip));
            when(bookingRepository.findByStudentIdAndTripId("S12345678", "T12AB34CD"))
                    .thenReturn(Optional.empty());
            when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
            when(bookingRepository.countByTripIdAndStatus("T12AB34CD", "CONFIRMED")).thenReturn(20);

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(201, response.get("statusCode"));
            verify(bookingRepository).save(any(Booking.class));
            verify(redisTemplate).delete(anyString());
        }
    }

    @Test
    void bookTrip_TripFull_AddsToWaitlist() {
        // Arrange
        Map<String, Object> event = createValidEvent();
        Student student = createValidStudent();
        Trip trip = createValidTrip();

        try (MockedStatic<AuthTokenUtil> authMock = mockStatic(AuthTokenUtil.class)) {
            authMock.when(() -> AuthTokenUtil.validateAuthToken(anyString()))
                    .thenReturn(Map.of("valid", true, "studentId", "S12345678"));
            
            when(studentRepository.findById("S12345678")).thenReturn(Optional.of(student));
            when(tripRepository.findById("T12AB34CD")).thenReturn(Optional.of(trip));
            when(bookingRepository.findByStudentIdAndTripId("S12345678", "T12AB34CD"))
                    .thenReturn(Optional.empty());
            when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
            when(bookingRepository.countByTripIdAndStatus("T12AB34CD", "CONFIRMED")).thenReturn(30); // Full
            when(bookingRepository.countByTripIdAndStatus("T12AB34CD", "WAITLIST")).thenReturn(2);

            // Act
            Map<String, Object> response = handler.handleRequest(event, null);

            // Assert
            assertEquals(201, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("WAITLIST"));
            verify(redisTemplate).delete(anyString());
        }
    }

    private Map<String, Object> createValidEvent() {
        return Map.of(
            "headers", Map.of("Authorization", "Bearer validtoken"),
            "body", "{\"tripId\":\"T12AB34CD\"}"
        );
    }

    private Student createValidStudent() {
        return new Student("S12345678", "test@college.edu", "Test Student", "password", "A101", "9876543210");
    }

    private Trip createValidTrip() {
        return new Trip("T12AB34CD", "CAMPUS_TO_CITY", LocalDate.now().plusDays(1), LocalTime.of(8, 30));
    }
}