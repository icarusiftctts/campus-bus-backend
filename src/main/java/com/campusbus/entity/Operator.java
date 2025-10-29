package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * OPERATOR ENTITY
 * 
 * Represents bus operators who authenticate via database credentials (not Cognito).
 * Used by the Mobile Operator App for login and trip management.
 * 
 * Database Table: operators
 * 
 * Key Features:
 * - Database-based authentication (separate from student Cognito auth)
 * - BCrypt password hashing for security
 * - Status tracking (ACTIVE/INACTIVE/SUSPENDED)
 * - Employee ID as unique identifier
 * - Last login tracking for audit purposes
 * 
 * Security Notes:
 * - Passwords are hashed using BCrypt (cost factor 10)
 * - Never store plain text passwords
 * - Employee ID is case-sensitive and unique
 * - Status must be ACTIVE for successful login
 * 
 * Integration with Mobile Operator App:
 * - OperatorLoginScreen uses employeeId + password
 * - All operator Lambda handlers validate operator tokens
 * - Trip assignments link operators to specific trips
 * 
 * Sample Data:
 * INSERT INTO operators VALUES 
 * ('OP001', 'op101', 'Test Operator', 
 *  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 *  '+919876543210', 'test@campus.edu', 'ACTIVE', NOW(), NULL);
 * Password hash is for "buspass"
 */
@Entity
@Table(name = "operators")
public class Operator {
    
    @Id
    @Column(name = "operator_id")
    private String operatorId;

    @Column(name = "employee_id", unique = true, nullable = false)
    private String employeeId;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String phone;
    private String email;
    
    @Enumerated(EnumType.STRING)
    private String status = "ACTIVE";
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    // Constructors
    public Operator() {}

    public Operator(String operatorId, String employeeId, String name, String passwordHash) {
        this.operatorId = operatorId;
        this.employeeId = employeeId;
        this.name = name;
        this.passwordHash = passwordHash;
    }

    // Getters and Setters
    public String getOperatorId() { 
        return operatorId; 
    }
    
    public void setOperatorId(String operatorId) { 
        this.operatorId = operatorId; 
    }
    
    public String getEmployeeId() { 
        return employeeId; 
    }
    
    public void setEmployeeId(String employeeId) { 
        this.employeeId = employeeId; 
    }
    
    public String getName() { 
        return name; 
    }
    
    public void setName(String name) { 
        this.name = name; 
    }
    
    public String getPasswordHash() { 
        return passwordHash; 
    }
    
    public void setPasswordHash(String passwordHash) { 
        this.passwordHash = passwordHash; 
    }
    
    public String getPhone() { 
        return phone; 
    }
    
    public void setPhone(String phone) { 
        this.phone = phone; 
    }
    
    public String getEmail() { 
        return email; 
    }
    
    public void setEmail(String email) { 
        this.email = email; 
    }
    
    public String getStatus() { 
        return status; 
    }
    
    public void setStatus(String status) { 
        this.status = status; 
    }
    
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    
    public void setCreatedAt(LocalDateTime createdAt) { 
        this.createdAt = createdAt; 
    }
    
    public LocalDateTime getLastLogin() { 
        return lastLogin; 
    }
    
    public void setLastLogin(LocalDateTime lastLogin) { 
        this.lastLogin = lastLogin; 
    }

    // Helper method to check if operator is active
    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }

    // Helper method to check if operator is suspended
    public boolean isSuspended() {
        return "SUSPENDED".equals(this.status);
    }

    @Override
    public String toString() {
        return "Operator{" +
                "operatorId='" + operatorId + '\'' +
                ", employeeId='" + employeeId + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", lastLogin=" + lastLogin +
                '}';
    }
}
