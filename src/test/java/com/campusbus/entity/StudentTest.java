package com.campusbus.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class StudentTest {

    @Test
    void isBlocked_PenaltyCountLessThan3_ReturnsFalse() {
        // Arrange
        Student student = new Student("S12345678", "test@college.edu", "Test Student", "password", "A101", "9876543210");
        student.setPenaltyCount(2);
        student.setBlockedUntil(LocalDateTime.now().plusDays(1));

        // Act & Assert
        assertFalse(student.isBlocked());
    }

    @Test
    void isBlocked_PenaltyCount3AndBlockedUntilFuture_ReturnsTrue() {
        // Arrange
        Student student = new Student("S12345678", "test@college.edu", "Test Student", "password", "A101", "9876543210");
        student.setPenaltyCount(3);
        student.setBlockedUntil(LocalDateTime.now().plusDays(1));

        // Act & Assert
        assertTrue(student.isBlocked());
    }

    @Test
    void isBlocked_PenaltyCount3AndBlockedUntilPast_ReturnsFalse() {
        // Arrange
        Student student = new Student("S12345678", "test@college.edu", "Test Student", "password", "A101", "9876543210");
        student.setPenaltyCount(3);
        student.setBlockedUntil(LocalDateTime.now().minusDays(1));

        // Act & Assert
        assertFalse(student.isBlocked());
    }

    @Test
    void isBlocked_PenaltyCount3AndBlockedUntilNull_ReturnsFalse() {
        // Arrange
        Student student = new Student("S12345678", "test@college.edu", "Test Student", "password", "A101", "9876543210");
        student.setPenaltyCount(3);
        student.setBlockedUntil(null);

        // Act & Assert
        assertFalse(student.isBlocked());
    }

    @Test
    void isBlocked_PenaltyCountGreaterThan3_ReturnsTrue() {
        // Arrange
        Student student = new Student("S12345678", "test@college.edu", "Test Student", "password", "A101", "9876543210");
        student.setPenaltyCount(5);
        student.setBlockedUntil(LocalDateTime.now().plusDays(1));

        // Act & Assert
        assertTrue(student.isBlocked());
    }
}