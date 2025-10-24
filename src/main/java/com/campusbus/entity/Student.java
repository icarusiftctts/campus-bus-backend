package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "students")
public class Student {
    @Id
    private String studentId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    // Password handled by AWS Cognito - not stored in database

    private String room;
    private String phone;
    private int penaltyCount = 0;
    private LocalDateTime blockedUntil;
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructors
    public Student() {}
    public Student(String studentId, String email, String name, String room, String phone) {
        this.studentId = studentId;
        this.email = email;
        this.name = name;
        this.room = room;
        this.phone = phone;
    }

    // Getters & Setters
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // Password methods removed - handled by AWS Cognito
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public int getPenaltyCount() { return penaltyCount; }
    public void setPenaltyCount(int penaltyCount) { this.penaltyCount = penaltyCount; }
    public LocalDateTime getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(LocalDateTime blockedUntil) { this.blockedUntil = blockedUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Helper method to check if student is currently blocked
    public boolean isBlocked() {
        return penaltyCount >= 3 && blockedUntil != null && blockedUntil.isAfter(LocalDateTime.now());
    }
}