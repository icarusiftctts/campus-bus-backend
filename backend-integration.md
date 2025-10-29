# Backend Integration Guide - Mobile Operator App

## 📋 Table of Contents
1. [Overview](#overview)
2. [Database Schema](#database-schema)
3. [Required Lambda Functions](#required-lambda-functions)
4. [API Endpoints](#api-endpoints)
5. [AWS Services Setup](#aws-services-setup)
6. [Authentication Flow](#authentication-flow)
7. [Screen-by-Screen Integration](#screen-by-screen-integration)
8. [Real-Time GPS Tracking](#real-time-gps-tracking)
9. [Error Handling](#error-handling)
10. [Testing Checklist](#testing-checklist)

---

## Overview

The Mobile Operator App requires **7 new Lambda functions** and **3 new database tables** to support operator authentication, trip management, QR scanning, passenger tracking, and misconduct reporting.

**Key Integration Points:**
- Operator authentication (separate from student Cognito pool)
- Trip selection and assignment
- QR code validation (reuses existing `ValidateQRHandler`)
- Passenger list retrieval
- Live GPS tracking via AWS IoT Core
- Misconduct reporting with photo upload to S3

**Cross-Reference with Student App:**
- Student app uses AWS Cognito for authentication → Operator app uses database-based auth
- Student app uses `BookTripHandler` → Operator app uses `ValidateQRHandler` (already exists)
- Both apps share the same `trips`, `bookings`, and `students` tables

---

## Database Schema

### New Tables Required

#### 1. `operators` Table
```sql
CREATE TABLE operators (
    operator_id VARCHAR(50) PRIMARY KEY,
    employee_id VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(15),
    email VARCHAR(100),
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    INDEX idx_employee_id (employee_id),
    INDEX idx_status (status)
);
```

**Sample Data:**
```sql
-- Password: 'buspass' hashed with BCrypt
INSERT INTO operators VALUES 
('OP001', 'op101', 'Rajesh Kumar', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
 '+919876543210', 'rajesh.operator@campus.edu', 'ACTIVE', NOW(), NULL);
```

#### 2. `trip_assignments` Table
```sql
CREATE TABLE trip_assignments (
    assignment_id VARCHAR(50) PRIMARY KEY,
    trip_id VARCHAR(50) NOT NULL,
    operator_id VARCHAR(50) NOT NULL,
    bus_number VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    status ENUM('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') DEFAULT 'ASSIGNED',
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
    FOREIGN KEY (operator_id) REFERENCES operators(operator_id),
    INDEX idx_trip_operator (trip_id, operator_id),
    INDEX idx_operator_status (operator_id, status)
);
```

#### 3. `misconduct_reports` Table
```sql
CREATE TABLE misconduct_reports (
    report_id VARCHAR(50) PRIMARY KEY,
    student_id VARCHAR(50) NOT NULL,
    trip_id VARCHAR(50) NOT NULL,
    operator_id VARCHAR(50) NOT NULL,
    reason ENUM('Misbehavior', 'Attempted boarding without valid QR', 'Other') NOT NULL,
    comments TEXT,
    photo_url VARCHAR(500),
    reported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('PENDING', 'REVIEWED', 'RESOLVED') DEFAULT 'PENDING',
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
    FOREIGN KEY (operator_id) REFERENCES operators(operator_id),
    INDEX idx_student_reports (student_id, reported_at DESC),
    INDEX idx_trip_reports (trip_id)
);
```

### Modifications to Existing Tables

**Update `trips` table:**
```sql
ALTER TABLE trips 
ADD COLUMN bus_number VARCHAR(20) AFTER capacity,
ADD COLUMN assigned_operator_id VARCHAR(50) AFTER bus_number,
ADD INDEX idx_operator (assigned_operator_id);
```

---

## Required Lambda Functions

### Summary Table

| Function | Purpose | Frontend Screen | Status |
|----------|---------|-----------------|--------|
| `OperatorLoginHandler` | Authenticate operator | OperatorLoginScreen | NEW |
| `GetOperatorTripsHandler` | Fetch available trips | BusSelectionScreen | NEW |
| `StartTripHandler` | Mark trip as started | BusSelectionScreen | NEW |
| `ValidateQRHandler` | Validate student QR | ScannerScreen | **EXISTS** |
| `GetPassengerListHandler` | Fetch passenger list | PassengerListScreen | NEW |
| `SubmitMisconductReportHandler` | Submit report | ReportingModal | NEW |
| `UpdateGPSLocationHandler` | Send GPS updates | GpsTrackingService | NEW |

---

### 1. OperatorLoginHandler (NEW)

**Purpose:** Authenticate bus operators using employee ID and password

**File Location:** `BookingSystemBackend/src/main/java/com/campusbus/lambda/OperatorLoginHandler.java`

**Frontend Integration:**
- **Screen:** `OperatorLoginScreen.tsx`
- **Trigger:** User clicks "Sign In" button
- **Request:**
```typescript
const response = await fetch('https://api-gateway-url/operator/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    employeeId: 'op101',
    password: 'buspass'
  })
});
```

**Response (Success - 200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "operatorId": "OP001",
  "employeeId": "op101",
  "name": "Rajesh Kumar",
  "message": "Login successful"
}
```

**Response (Error - 401):**
```json
{
  "message": "Invalid credentials"
}
```

**Implementation:**
```java
package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.Operator;
import com.campusbus.repository.OperatorRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;

public class OperatorLoginHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static OperatorRepository operatorRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            operatorRepository = context.getBean(OperatorRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);

            String employeeId = (String) body.get("employeeId");
            String password = (String) body.get("password");

            if (employeeId == null || password == null) {
                return createErrorResponse(400, "Missing employeeId or password");
            }

            Operator operator = operatorRepository.findByEmployeeId(employeeId);
            if (operator == null) {
                return createErrorResponse(401, "Invalid credentials");
            }

            if (!"ACTIVE".equals(operator.getStatus())) {
                return createErrorResponse(403, "Operator account is " + operator.getStatus());
            }

            if (!passwordEncoder.matches(password, operator.getPasswordHash())) {
                return createErrorResponse(401, "Invalid credentials");
            }

            operator.setLastLogin(LocalDateTime.now());
            operatorRepository.save(operator);

            String authToken = AuthTokenUtil.generateOperatorToken(operator.getOperatorId(), employeeId);

            Map<String, Object> responseData = Map.of(
                "token", authToken,
                "operatorId", operator.getOperatorId(),
                "employeeId", operator.getEmployeeId(),
                "name", operator.getName(),
                "message", "Login successful"
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}
```

**Required Entity:** `BookingSystemBackend/src/main/java/com/campusbus/entity/Operator.java`
```java
package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "operators")
public class Operator {
    @Id
    private String operatorId;

    @Column(unique = true, nullable = false)
    private String employeeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String passwordHash;

    private String phone;
    private String email;
    private String status = "ACTIVE";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastLogin;

    public Operator() {}

    // Getters and Setters
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
}
```

**Required Repository:** `BookingSystemBackend/src/main/java/com/campusbus/repository/OperatorRepository.java`
```java
package com.campusbus.repository;

import com.campusbus.entity.Operator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OperatorRepository extends JpaRepository<Operator, String> {
    
    @Query("SELECT o FROM Operator o WHERE o.employeeId = ?1")
    Operator findByEmployeeId(String employeeId);
    
    @Query("SELECT o FROM Operator o WHERE o.operatorId = ?1 AND o.status = 'ACTIVE'")
    Operator findActiveOperator(String operatorId);
}
```

**Update AuthTokenUtil:** Add to `BookingSystemBackend/src/main/java/com/campusbus/util/AuthTokenUtil.java`
```java
public static String generateOperatorToken(String operatorId, String employeeId) {
    Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
    return JWT.create()
        .withSubject(operatorId)
        .withClaim("employeeId", employeeId)
        .withClaim("role", "OPERATOR")
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
        .sign(algorithm);
}
```

---

### 2. GetOperatorTripsHandler (NEW)

**Purpose:** Retrieve available and assigned trips for operator

**File Location:** `BookingSystemBackend/src/main/java/com/campusbus/lambda/GetOperatorTripsHandler.java`

**Frontend Integration:**
- **Screen:** `BusSelectionScreen.tsx`
- **Trigger:** Screen loads (useEffect)
- **Request:**
```typescript
const response = await fetch('https://api-gateway-url/operator/trips', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${operatorToken}`,
    'Content-Type': 'application/json'
  }
});
```

**Response (Success - 200):**
```json
{
  "trips": [
    {
      "id": "T401",
      "time": "18:30:00",
      "busNumber": "Bus #05",
      "route": "Campus → City",
      "status": "Upcoming"
    },
    {
      "id": "T402",
      "time": "19:00:00",
      "busNumber": "Bus #12",
      "route": "City → Campus",
      "status": "Active"
    }
  ],
  "date": "2024-01-15"
}
```

**Implementation:**
```java
package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.repository.TripRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class GetOperatorTripsHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static TripRepository tripRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            tripRepository = context.getBean(TripRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return createErrorResponse(401, "Missing or invalid authorization header");
            }

            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateToken(token);
            
            if (!(Boolean) tokenData.get("valid")) {
                return createErrorResponse(401, "Invalid token");
            }

            String operatorId = (String) tokenData.get("operatorId");

            LocalDate today = LocalDate.now();
            List<Map<String, Object>> trips = tripRepository.findOperatorTrips(operatorId, today);

            Map<String, Object> responseData = Map.of(
                "trips", trips,
                "date", today.toString()
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}
```

**Add to TripRepository:**
```java
@Query(value = """
    SELECT 
        t.trip_id as id,
        TIME_FORMAT(t.departure_time, '%H:%i:%s') as time,
        COALESCE(t.bus_number, 'Unassigned') as busNumber,
        CASE 
            WHEN t.route = 'CAMPUS_TO_CITY' THEN 'Campus → City'
            WHEN t.route = 'CITY_TO_CAMPUS' THEN 'City → Campus'
            ELSE t.route
        END as route,
        CASE 
            WHEN ta.status = 'IN_PROGRESS' THEN 'Active'
            WHEN ta.status = 'COMPLETED' THEN 'Completed'
            WHEN t.departure_time < CURRENT_TIME() THEN 'Completed'
            ELSE 'Upcoming'
        END as status
    FROM trips t
    LEFT JOIN trip_assignments ta ON t.trip_id = ta.trip_id AND ta.operator_id = ?1
    WHERE t.trip_date = ?2 AND t.status = 'ACTIVE'
    ORDER BY t.departure_time
    """, nativeQuery = true)
List<Map<String, Object>> findOperatorTrips(String operatorId, LocalDate tripDate);
```

---

### 3. StartTripHandler (NEW)

**Purpose:** Mark trip as started and create assignment

**File Location:** `BookingSystemBackend/src/main/java/com/campusbus/lambda/StartTripHandler.java`

**Frontend Integration:**
- **Screen:** `BusSelectionScreen.tsx`
- **Trigger:** User confirms trip selection in modal
- **Request:**
```typescript
const response = await fetch('https://api-gateway-url/operator/trips/start', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${operatorToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    tripId: 'T401',
    busNumber: 'Bus #05'
  })
});
```

**Response (Success - 200):**
```json
{
  "assignmentId": "TA12AB34",
  "tripId": "T401",
  "status": "IN_PROGRESS",
  "message": "Trip started successfully"
}
```

**Implementation:**
```java
package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.entity.TripAssignment;
import com.campusbus.repository.TripAssignmentRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class StartTripHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static TripAssignmentRepository assignmentRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            assignmentRepository = context.getBean(TripAssignmentRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateToken(token);
            String operatorId = (String) tokenData.get("operatorId");

            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);
            String tripId = (String) body.get("tripId");
            String busNumber = (String) body.get("busNumber");

            TripAssignment assignment = new TripAssignment();
            assignment.setAssignmentId("TA" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            assignment.setTripId(tripId);
            assignment.setOperatorId(operatorId);
            assignment.setBusNumber(busNumber);
            assignment.setStartedAt(LocalDateTime.now());
            assignment.setStatus("IN_PROGRESS");

            assignmentRepository.save(assignment);

            Map<String, Object> responseData = Map.of(
                "assignmentId", assignment.getAssignmentId(),
                "tripId", tripId,
                "status", "IN_PROGRESS",
                "message", "Trip started successfully"
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}
```

**Required Entity:** `BookingSystemBackend/src/main/java/com/campusbus/entity/TripAssignment.java`
```java
package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_assignments")
public class TripAssignment {
    @Id
    private String assignmentId;
    
    @Column(nullable = false)
    private String tripId;
    
    @Column(nullable = false)
    private String operatorId;
    
    @Column(nullable = false)
    private String busNumber;
    
    private LocalDateTime assignedAt = LocalDateTime.now();
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String status = "ASSIGNED";

    public TripAssignment() {}

    // Getters and Setters
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getBusNumber() { return busNumber; }
    public void setBusNumber(String busNumber) { this.busNumber = busNumber; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

**Required Repository:** `BookingSystemBackend/src/main/java/com/campusbus/repository/TripAssignmentRepository.java`
```java
package com.campusbus.repository;

import com.campusbus.entity.TripAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripAssignmentRepository extends JpaRepository<TripAssignment, String> {
}
```

---

### 4. ValidateQRHandler (ALREADY EXISTS)

**Purpose:** Validate student QR codes and mark as boarded

**File Location:** `BookingSystemBackend/src/main/java/com/campusbus/lambda/ValidateQRHandler.java`

**Frontend Integration:**
- **Screen:** `ScannerScreen.tsx`
- **Trigger:** QR code scanned by camera
- **Request:**
```typescript
const response = await fetch('https://api-gateway-url/api/qr/validate', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${operatorToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    qrToken: scannedQRData,
    tripId: currentTripId
  })
});
```

**Response (Valid QR - 200):**
```json
{
  "valid": true,
  "status": "SCANNED",
  "bookingId": "B56EF78GH",
  "studentId": "S12AB34CD",
  "message": "QR code validated successfully"
}
```

**Response (Already Scanned - 200):**
```json
{
  "valid": true,
  "status": "ALREADY_SCANNED",
  "message": "QR code already scanned"
}
```

**Response (Invalid QR - 400):**
```json
{
  "valid": false,
  "message": "Invalid QR code"
}
```

**Frontend Handling:**
```typescript
const onScanSuccess = async (e: { data: string }) => {
  const response = await fetch(API_URL, {
    method: 'POST',
    body: JSON.stringify({ qrToken: e.data, tripId })
  });
  
  const result = await response.json();
  
  if (result.valid && result.status === 'SCANNED') {
    setScanStatus('valid');
    setScannedName(result.studentId);
    setBoardedCount(prev => prev + 1);
  } else if (result.status === 'ALREADY_SCANNED') {
    setScanStatus('duplicate');
  } else {
    setScanStatus('invalid');
  }
};
```

**Note:** This handler is already implemented and tested. No changes needed. Reuse as-is.

---

### 5. GetPassengerListHandler (NEW)

**Purpose:** Retrieve list of booked passengers for a trip

**File Location:** `BookingSystemBackend/src/main/java/com/campusbus/lambda/GetPassengerListHandler.java`

**Frontend Integration:**
- **Screen:** `PassengerListScreen.tsx`
- **Trigger:** Screen loads or "View Passenger List" button clicked
- **Request:**
```typescript
const response = await fetch(`https://api-gateway-url/operator/trips/${tripId}/passengers`, {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${operatorToken}`,
    'Content-Type': 'application/json'
  }
});
```

**Response (Success - 200):**
```json
{
  "tripId": "T401",
  "passengers": [
    {
      "id": "STU101",
      "name": "Alice Johnson",
      "status": "SCANNED",
      "boardingStatus": "Boarded"
    },
    {
      "id": "STU102",
      "name": "Bob Williams",
      "status": "CONFIRMED",
      "boardingStatus": "Not Boarded"
    }
  ],
  "totalCount": 25
}
```

**Implementation:**
```java
package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.repository.BookingRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;

public class GetPassengerListHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static BookingRepository bookingRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            bookingRepository = context.getBean(BookingRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateToken(token);

            if (!(Boolean) tokenData.get("valid")) {
                return createErrorResponse(401, "Invalid token");
            }

            Map<String, String> pathParams = (Map<String, String>) event.get("pathParameters");
            String tripId = pathParams.get("tripId");

            List<Map<String, Object>> passengers = bookingRepository.findPassengersByTripId(tripId);

            Map<String, Object> responseData = Map.of(
                "tripId", tripId,
                "passengers", passengers,
                "totalCount", passengers.size()
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}
```

**Add to BookingRepository:**
```java
@Query(value = """
    SELECT 
        s.student_id as id,
        s.name,
        b.status,
        CASE 
            WHEN b.status = 'SCANNED' THEN 'Boarded'
            ELSE 'Not Boarded'
        END as boardingStatus
    FROM bookings b
    JOIN students s ON b.student_id = s.student_id
    WHERE b.trip_id = ?1 AND b.status IN ('CONFIRMED', 'SCANNED')
    ORDER BY s.name
    """, nativeQuery = true)
List<Map<String, Object>> findPassengersByTripId(String tripId);
```

---

### 6. SubmitMisconductReportHandler (NEW)

**Purpose:** Submit misconduct report with optional photo

**File Location:** `BookingSystemBackend/src/main/java/com/campusbus/lambda/SubmitMisconductReportHandler.java`

**Frontend Integration:**
- **Screen:** `ReportingModal.tsx`
- **Trigger:** User clicks "Submit Report" button
- **Request:**
```typescript
const response = await fetch('https://api-gateway-url/operator/reports', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${operatorToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    studentId: 'STU101',
    tripId: 'T401',
    reason: 'Misbehavior',
    comments: 'Student was disruptive during boarding',
    photoBase64: '/9j/4AAQSkZJRgABAQAAAQABAAD...' // Optional
  })
});
```

**Response (Success - 201):**
```json
{
  "reportId": "MR12AB34",
  "message": "Report submitted successfully"
}
```

**Implementation:**
```java
package com.campusbus.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.campusbus.entity.MisconductReport;
import com.campusbus.repository.MisconductReportRepository;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class SubmitMisconductReportHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static ConfigurableApplicationContext context;
    private static MisconductReportRepository reportRepository;
    private static AmazonS3 s3Client;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String S3_BUCKET = System.getenv("MISCONDUCT_PHOTOS_BUCKET");

    static {
        s3Client = AmazonS3ClientBuilder.defaultClient();
    }

    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(com.campusbus.booking_system.BookingSystemApplication.class);
            reportRepository = context.getBean(MisconductReportRepository.class);
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            initializeSpringContext();

            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateToken(token);
            String operatorId = (String) tokenData.get("operatorId");

            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);

            String studentId = (String) body.get("studentId");
            String tripId = (String) body.get("tripId");
            String reason = (String) body.get("reason");
            String comments = (String) body.get("comments");
            String photoBase64 = (String) body.get("photoBase64");

            String photoUrl = null;
            if (photoBase64 != null && !photoBase64.isEmpty()) {
                photoUrl = uploadPhotoToS3(photoBase64, studentId);
            }

            MisconductReport report = new MisconductReport();
            report.setReportId("MR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            report.setStudentId(studentId);
            report.setTripId(tripId);
            report.setOperatorId(operatorId);
            report.setReason(reason);
            report.setComments(comments);
            report.setPhotoUrl(photoUrl);
            report.setReportedAt(LocalDateTime.now());
            report.setStatus("PENDING");

            reportRepository.save(report);

            Map<String, Object> responseData = Map.of(
                "reportId", report.getReportId(),
                "message", "Report submitted successfully"
            );

            return createSuccessResponse(201, responseData);

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private String uploadPhotoToS3(String base64Photo, String studentId) {
        try {
            byte[] photoBytes = Base64.getDecoder().decode(base64Photo);
            String fileName = "misconduct/" + studentId + "/" + UUID.randomUUID() + ".jpg";

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(photoBytes.length);
            metadata.setContentType("image/jpeg");

            s3Client.putObject(S3_BUCKET, fileName, new ByteArrayInputStream(photoBytes), metadata);

            return s3Client.getUrl(S3_BUCKET, fileName).toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload photo: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}
```

**Required Entity:** `BookingSystemBackend/src/main/java/com/campusbus/entity/MisconductReport.java`
```java
package com.campusbus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "misconduct_reports")
public class MisconductReport {
    @Id
    private String reportId;
    
    @Column(nullable = false)
    private String studentId;
    
    @Column(nullable = false)
    private String tripId;
    
    @Column(nullable = false)
    private String operatorId;
    
    @Column(nullable = false)
    private String reason;
    
    @Column(columnDefinition = "TEXT")
    private String comments;
    
    private String photoUrl;
    private LocalDateTime reportedAt = LocalDateTime.now();
    private String status = "PENDING";

    public MisconductReport() {}

    // Getters and Setters
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDateTime reportedAt) { this.reportedAt = reportedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

**Required Repository:** `BookingSystemBackend/src/main/java/com/campusbus/repository/MisconductReportRepository.java`
```java
package com.campusbus.repository;

import com.campusbus.entity.MisconductReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MisconductReportRepository extends JpaRepository<MisconductReport, String> {
}
```

---

### 7. UpdateGPSLocationHandler (NEW)

**Purpose:** Receive and broadcast GPS location updates from operator

**File Location:** `BookingSystemBackend/src/main/java/com/campusbus/lambda/UpdateGPSLocationHandler.java`

**Frontend Integration:**
- **Service:** `GpsTrackingService.tsx`
- **Trigger:** Every 30 seconds while trip is active
- **Request:**
```typescript
const response = await fetch('https://api-gateway-url/operator/gps', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${operatorToken}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    tripId: 'T401',
    latitude: 12.9716,
    longitude: 77.5946,
    speed: 45.5,
    timestamp: new Date().toISOString()
  })
});
```

**Response (Success - 200):**
```json
{
  "message": "GPS location updated successfully",
  "timestamp": "2024-01-15T18:45:30Z"
}
```

**Implementation:**
```java
package com.campusbus.lambda;

import com.amazonaws.services.iotdata.AWSIotData;
import com.amazonaws.services.iotdata.AWSIotDataClientBuilder;
import com.amazonaws.services.iotdata.model.PublishRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.campusbus.util.AuthTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.util.Map;

public class UpdateGPSLocationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static AWSIotData iotClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        iotClient = AWSIotDataClientBuilder.defaultClient();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            Map<String, Object> headers = (Map<String, Object>) event.get("headers");
            String authHeader = (String) headers.get("Authorization");
            String token = authHeader.substring(7);
            Map<String, Object> tokenData = AuthTokenUtil.validateToken(token);

            if (!(Boolean) tokenData.get("valid")) {
                return createErrorResponse(401, "Invalid token");
            }

            String requestBody = (String) event.get("body");
            Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);

            String tripId = (String) body.get("tripId");
            Double latitude = ((Number) body.get("latitude")).doubleValue();
            Double longitude = ((Number) body.get("longitude")).doubleValue();
            Double speed = body.get("speed") != null ? ((Number) body.get("speed")).doubleValue() : 0.0;
            String timestamp = (String) body.get("timestamp");

            Map<String, Object> gpsData = Map.of(
                "tripId", tripId,
                "latitude", latitude,
                "longitude", longitude,
                "speed", speed,
                "timestamp", timestamp
            );

            String topic = "bus/location/" + tripId;
            String payload = objectMapper.writeValueAsString(gpsData);

            PublishRequest publishRequest = new PublishRequest()
                .withTopic(topic)
                .withQos(1)
                .withPayload(ByteBuffer.wrap(payload.getBytes()));

            iotClient.publish(publishRequest);

            Map<String, Object> responseData = Map.of(
                "message", "GPS location updated successfully",
                "timestamp", timestamp
            );

            return createSuccessResponse(200, responseData);

        } catch (Exception e) {
            return createErrorResponse(500, "Error: " + e.getMessage());
        }
    }

    private Map<String, Object> createSuccessResponse(int statusCode, Map<String, Object> data) {
        try {
            return Map.of(
                "statusCode", statusCode,
                "headers", Map.of("Content-Type", "application/json"),
                "body", objectMapper.writeValueAsString(data)
            );
        } catch (Exception e) {
            return createErrorResponse(500, "Error serializing response");
        }
    }

    private Map<String, Object> createErrorResponse(int statusCode, String message) {
        return Map.of(
            "statusCode", statusCode,
            "headers", Map.of("Content-Type", "application/json"),
            "body", "{\"message\":\"" + message + "\"}"
        );
    }
}
```

---

## API Endpoints

### API Gateway Configuration

**Base URL:** `https://{api-id}.execute-api.ap-south-1.amazonaws.com/prod`

| Endpoint | Method | Lambda Function | Auth Required | Frontend Screen |
|----------|--------|-----------------|---------------|-----------------|
| `/operator/login` | POST | OperatorLoginHandler | No | OperatorLoginScreen |
| `/operator/trips` | GET | GetOperatorTripsHandler | Yes | BusSelectionScreen |
| `/operator/trips/start` | POST | StartTripHandler | Yes | BusSelectionScreen |
| `/api/qr/validate` | POST | ValidateQRHandler | Yes | ScannerScreen |
| `/operator/trips/{tripId}/passengers` | GET | GetPassengerListHandler | Yes | PassengerListScreen |
| `/operator/reports` | POST | SubmitMisconductReportHandler | Yes | ReportingModal |
| `/operator/gps` | POST | UpdateGPSLocationHandler | Yes | GpsTrackingService |

### CORS Configuration

**Required for all endpoints:**
```json
{
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Content-Type,Authorization",
  "Access-Control-Allow-Methods": "GET,POST,PUT,DELETE,OPTIONS"
}
```

### API Gateway Resource Structure

```
/
├── operator/
│   ├── login (POST)
│   ├── trips (GET)
│   │   ├── start (POST)
│   │   └── {tripId}/
│   │       └── passengers (GET)
│   ├── reports (POST)
│   └── gps (POST)
└── api/
    └── qr/
        └── validate (POST)
```

---

## AWS Services Setup

### 1. RDS MySQL Database

**Configuration:**
```yaml
Engine: MySQL 8.0
Instance Class: db.t3.micro
Storage: 20GB GP2
Multi-AZ: No (for cost saving)
Database Name: campusbus
VPC: Default VPC
Security Group: Allow 3306 from Lambda security group
```

**Connection String:**
```
jdbc:mysql://{rds-endpoint}:3306/campusbus
```

**Environment Variables for Lambda:**
```
DB_HOST=campusbus.xxxxx.ap-south-1.rds.amazonaws.com
DB_PORT=3306
DB_NAME=campusbus
DB_USERNAME=admin
DB_PASSWORD={stored-in-secrets-manager}
```

### 2. S3 Bucket for Photos

**Bucket Name:** `campusbus-misconduct-photos`

**Folder Structure:**
```
campusbus-misconduct-photos/
└── misconduct/
    ├── STU101/
    │   ├── uuid1.jpg
    │   └── uuid2.jpg
    └── STU102/
        └── uuid3.jpg
```

**Lifecycle Policy:**
```json
{
  "Rules": [
    {
      "Id": "ArchiveOldPhotos",
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 90,
          "StorageClass": "GLACIER"
        }
      ]
    }
  ]
}
```

**IAM Policy for Lambda:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::campusbus-misconduct-photos/*"
    }
  ]
}
```

### 3. AWS IoT Core for GPS Tracking

**IoT Topic Structure:**
```
bus/location/{tripId}
```

**IoT Policy:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "iot:Publish",
      "Resource": "arn:aws:iot:ap-south-1:*:topic/bus/location/*"
    },
    {
      "Effect": "Allow",
      "Action": "iot:Subscribe",
      "Resource": "arn:aws:iot:ap-south-1:*:topicfilter/bus/location/*"
    }
  ]
}
```

**Student App Subscription:**
```typescript
// Student app subscribes to receive GPS updates
const mqttClient = new Paho.MQTT.Client(
  'xxxxx.iot.ap-south-1.amazonaws.com',
  443,
  'student-' + studentId
);

mqttClient.subscribe('bus/location/' + tripId);

mqttClient.onMessageArrived = (message) => {
  const gpsData = JSON.parse(message.payloadString);
  updateBusMarkerOnMap(gpsData.latitude, gpsData.longitude);
};
```

### 4. ElastiCache Redis (Optional)

**Purpose:** Cache trip data and operator sessions

**Configuration:**
```yaml
Node Type: cache.t3.micro
Engine: Redis 7.0
Cluster Mode: Disabled
```

**Usage:**
```java
// Cache operator trips for 5 minutes
redisTemplate.opsForValue().set(
  "operator:trips:" + operatorId,
  tripsJson,
  5,
  TimeUnit.MINUTES
);
```

### 5. Lambda Execution Role

**IAM Role:** `CampusBusOperatorLambdaRole`

**Attached Policies:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ec2:CreateNetworkInterface",
        "ec2:DescribeNetworkInterfaces",
        "ec2:DeleteNetworkInterface"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::campusbus-misconduct-photos/*"
    },
    {
      "Effect": "Allow",
      "Action": "iot:Publish",
      "Resource": "arn:aws:iot:ap-south-1:*:topic/bus/location/*"
    }
  ]
}
```

---

## Authentication Flow

### Operator Login Flow

```
1. Operator enters employeeId and password
   ↓
2. Frontend sends POST to /operator/login
   ↓
3. OperatorLoginHandler validates credentials
   ↓
4. BCrypt verifies password hash
   ↓
5. Generate JWT token with operatorId and role
   ↓
6. Return token to frontend
   ↓
7. Frontend stores token in AsyncStorage
   ↓
8. All subsequent requests include: Authorization: Bearer {token}
```

### Token Structure

**Operator JWT Token:**
```json
{
  "sub": "OP001",
  "employeeId": "op101",
  "role": "OPERATOR",
  "iat": 1705334400,
  "exp": 1705420800
}
```

**Token Validation:**
```java
public static Map<String, Object> validateToken(String token) {
    try {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        JWTVerifier verifier = JWT.require(algorithm).build();
        DecodedJWT jwt = verifier.verify(token);
        
        return Map.of(
            "valid", true,
            "operatorId", jwt.getSubject(),
            "employeeId", jwt.getClaim("employeeId").asString(),
            "role", jwt.getClaim("role").asString()
        );
    } catch (Exception e) {
        return Map.of("valid", false);
    }
}
```

### Comparison with Student Authentication

| Aspect | Student App | Operator App |
|--------|-------------|--------------|
| Auth Provider | AWS Cognito | Database + BCrypt |
| Token Type | Cognito JWT | Custom JWT |
| Token Expiry | 1 hour | 24 hours |
| Password Reset | Cognito email flow | Admin manual reset |
| MFA Support | Yes (optional) | No |

---

## Screen-by-Screen Integration

### 1. OperatorLoginScreen.tsx

**Backend Dependencies:**
- Lambda: `OperatorLoginHandler`
- Database: `operators` table
- API Endpoint: `POST /operator/login`

**Integration Code:**
```typescript
const handleLoginClick = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/operator/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        employeeId: employeeId,
        password: password
      })
    });

    const data = await response.json();

    if (response.ok) {
      await AsyncStorage.setItem('operatorToken', data.token);
      await AsyncStorage.setItem('operatorId', data.operatorId);
      await AsyncStorage.setItem('operatorName', data.name);
      onLoginSuccess();
    } else {
      Alert.alert('Login Failed', data.message);
    }
  } catch (error) {
    Alert.alert('Error', 'Network error. Please try again.');
  }
};
```

**Error Handling:**
- 400: Missing credentials → Show "Please enter both fields"
- 401: Invalid credentials → Show "Invalid Employee ID or Password"
- 403: Account suspended → Show "Your account is suspended. Contact admin."
- 500: Server error → Show "Server error. Please try again later."

---

### 2. BusSelectionScreen.tsx

**Backend Dependencies:**
- Lambda: `GetOperatorTripsHandler`, `StartTripHandler`
- Database: `trips`, `trip_assignments` tables
- API Endpoints: 
  - `GET /operator/trips`
  - `POST /operator/trips/start`

**Integration Code:**
```typescript
// Fetch trips on screen load
useEffect(() => {
  const loadTrips = async () => {
    try {
      const token = await AsyncStorage.getItem('operatorToken');
      const response = await fetch(`${API_BASE_URL}/operator/trips`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      const data = await response.json();
      setAvailableTrips(data.trips);
      setIsLoading(false);
    } catch (error) {
      setIsLoading(false);
      Alert.alert('Error', 'Failed to load trips');
    }
  };

  loadTrips();
}, []);

// Start trip when confirmed
const confirmStartShift = async () => {
  try {
    const token = await AsyncStorage.getItem('operatorToken');
    const response = await fetch(`${API_BASE_URL}/operator/trips/start`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        tripId: selectedTrip.id,
        busNumber: selectedTrip.busNumber
      })
    });

    const data = await response.json();

    if (response.ok) {
      await AsyncStorage.setItem('currentTripId', selectedTrip.id);
      setShowConfirmModal(false);
      onStartScanning(selectedTrip.id);
    } else {
      Alert.alert('Error', data.message);
    }
  } catch (error) {
    Alert.alert('Error', 'Failed to start trip');
  }
};
```

**Data Mapping:**
```typescript
interface Trip {
  id: string;           // Maps to trip_id
  time: string;         // Maps to departure_time (formatted)
  busNumber: string;    // Maps to bus_number
  route: string;        // Maps to route (formatted)
  status: 'Active' | 'Upcoming' | 'Completed';  // Derived from trip_assignments
}
```

---

### 3. ScannerScreen.tsx

**Backend Dependencies:**
- Lambda: `ValidateQRHandler` (EXISTING)
- Database: `bookings` table
- API Endpoint: `POST /api/qr/validate`

**Integration Code:**
```typescript
const onScanSuccess = async (e: { data: string }) => {
  if (!isScanning) return;
  setIsScanning(false);

  try {
    const token = await AsyncStorage.getItem('operatorToken');
    const tripId = await AsyncStorage.getItem('currentTripId');

    const response = await fetch(`${API_BASE_URL}/api/qr/validate`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        qrToken: e.data,
        tripId: tripId
      })
    });

    const data = await response.json();

    if (data.valid && data.status === 'SCANNED') {
      setScannedName(data.studentId);
      setScanStatus('valid');
      setBoardedCount(prev => prev + 1);
      playSuccessSound();
    } else if (data.status === 'ALREADY_SCANNED') {
      setScanStatus('duplicate');
      playWarningSound();
    } else {
      setScanStatus('invalid');
      playErrorSound();
    }

    // Show feedback for 2 seconds
    Animated.sequence([
      Animated.timing(fadeAnim, { toValue: 1, duration: 300, useNativeDriver: true }),
      Animated.delay(1700),
      Animated.timing(fadeAnim, { toValue: 0, duration: 300, useNativeDriver: true }),
    ]).start(() => {
      setScanStatus(null);
      setIsScanning(true);
    });

  } catch (error) {
    Alert.alert('Error', 'Failed to validate QR code');
    setIsScanning(true);
  }
};
```

**Visual Feedback Mapping:**
```typescript
const getOverlayStyle = () => {
  if (scanStatus === 'valid') return { 
    borderColor: '#38A169', 
    text: 'VALID',
    backgroundColor: 'rgba(56, 161, 105, 0.9)'
  };
  if (scanStatus === 'invalid') return { 
    borderColor: '#E53E3E', 
    text: 'INVALID QR',
    backgroundColor: 'rgba(229, 62, 62, 0.9)'
  };
  if (scanStatus === 'duplicate') return { 
    borderColor: '#ECC94B', 
    text: 'ALREADY SCANNED',
    backgroundColor: 'rgba(236, 201, 75, 0.9)'
  };
  return { borderColor: 'transparent', text: '' };
};
```

**Cross-Reference with Student App:**
- Student app generates QR using `QRCodeGenerator.generateQRToken(bookingId, tripId)`
- Operator app validates using same `ValidateQRHandler`
- Both use same `bookings` table for status updates

---

### 4. PassengerListScreen.tsx

**Backend Dependencies:**
- Lambda: `GetPassengerListHandler`
- Database: `bookings`, `students` tables (JOIN)
- API Endpoint: `GET /operator/trips/{tripId}/passengers`

**Integration Code:**
```typescript
useEffect(() => {
  const fetchPassengers = async () => {
    try {
      const token = await AsyncStorage.getItem('operatorToken');
      const response = await fetch(
        `${API_BASE_URL}/operator/trips/${tripId}/passengers`,
        {
          method: 'GET',
          headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
          }
        }
      );

      const data = await response.json();
      setPassengers(data.passengers);
      setIsLoading(false);
    } catch (error) {
      setIsLoading(false);
      Alert.alert('Error', 'Failed to load passenger list');
    }
  };

  fetchPassengers();
}, [tripId]);
```

**Data Mapping:**
```typescript
interface Passenger {
  id: string;              // student_id
  name: string;            // student name
  status: string;          // booking status (CONFIRMED/SCANNED)
  boardingStatus: string;  // Derived: "Boarded" or "Not Boarded"
}
```

**Real-Time Updates:**
```typescript
// Refresh passenger list when returning from scanner
useEffect(() => {
  const unsubscribe = navigation.addListener('focus', () => {
    fetchPassengers();
  });
  return unsubscribe;
}, [navigation]);
```

---

### 5. ReportingModal.tsx

**Backend Dependencies:**
- Lambda: `SubmitMisconductReportHandler`
- Database: `misconduct_reports` table
- S3: Photo storage
- API Endpoint: `POST /operator/reports`

**Integration Code:**
```typescript
const handleSubmit = async () => {
  if (!reason) {
    Alert.alert('Validation Error', 'Please select a reason');
    return;
  }

  try {
    const token = await AsyncStorage.getItem('operatorToken');
    const tripId = await AsyncStorage.getItem('currentTripId');

    // Convert photo to base64 if exists
    let photoBase64 = null;
    if (photo && photo.uri) {
      const response = await fetch(photo.uri);
      const blob = await response.blob();
      photoBase64 = await blobToBase64(blob);
    }

    const response = await fetch(`${API_BASE_URL}/operator/reports`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        studentId: student.id,
        tripId: tripId,
        reason: reason,
        comments: comments.trim(),
        photoBase64: photoBase64
      })
    });

    const data = await response.json();

    if (response.ok) {
      Alert.alert('Success', 'Report submitted successfully');
      onSubmit(data);
      onClose();
    } else {
      Alert.alert('Error', data.message);
    }
  } catch (error) {
    Alert.alert('Error', 'Failed to submit report');
  }
};

const blobToBase64 = (blob: Blob): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const base64 = reader.result?.toString().split(',')[1];
      resolve(base64 || '');
    };
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
};
```

**Photo Upload Flow:**
```
1. User takes photo with camera
   ↓
2. Photo stored locally as URI
   ↓
3. Convert to base64 on submit
   ↓
4. Send base64 in JSON body
   ↓
5. Lambda decodes base64
   ↓
6. Upload to S3 bucket
   ↓
7. Store S3 URL in database
```

---

### 6. GpsTrackingService.tsx

**Backend Dependencies:**
- Lambda: `UpdateGPSLocationHandler`
- AWS IoT Core: Topic `bus/location/{tripId}`
- API Endpoint: `POST /operator/gps`

**Implementation:**
```typescript
import Geolocation from '@react-native-community/geolocation';
import AsyncStorage from '@react-native-async-storage/async-storage';

class GpsTrackingService {
  private intervalId: NodeJS.Timeout | null = null;
  private isTracking: boolean = false;

  startTracking = async () => {
    if (this.isTracking) return;
    
    this.isTracking = true;
    const tripId = await AsyncStorage.getItem('currentTripId');
    const token = await AsyncStorage.getItem('operatorToken');

    // Send GPS every 30 seconds
    this.intervalId = setInterval(async () => {
      Geolocation.getCurrentPosition(
        async (position) => {
          try {
            await fetch(`${API_BASE_URL}/operator/gps`, {
              method: 'POST',
              headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
              },
              body: JSON.stringify({
                tripId: tripId,
                latitude: position.coords.latitude,
                longitude: position.coords.longitude,
                speed: position.coords.speed || 0,
                timestamp: new Date().toISOString()
              })
            });
          } catch (error) {
            console.error('Failed to send GPS update:', error);
          }
        },
        (error) => console.error('GPS error:', error),
        { enableHighAccuracy: true, timeout: 20000, maximumAge: 1000 }
      );
    }, 30000); // 30 seconds
  };

  stopTracking = () => {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    this.isTracking = false;
  };
}

export default new GpsTrackingService();
```

**Usage in ScannerScreen:**
```typescript
useEffect(() => {
  // Start GPS tracking when scanner screen loads
  GpsTrackingService.startTracking();

  return () => {
    // Stop GPS tracking when leaving screen
    GpsTrackingService.stopTracking();
  };
}, []);
```

**Student App Integration:**
```typescript
// Student app subscribes to receive updates
import mqtt from 'react-native-mqtt';

const client = mqtt.connect('wss://xxxxx.iot.ap-south-1.amazonaws.com/mqtt');

client.on('connect', () => {
  client.subscribe(`bus/location/${tripId}`);
});

client.on('message', (topic, message) => {
  const gpsData = JSON.parse(message.toString());
  updateBusMarker({
    latitude: gpsData.latitude,
    longitude: gpsData.longitude
  });
});
```

---

## Error Handling

### Common Error Scenarios

#### 1. Network Errors
```typescript
try {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
} catch (error) {
  if (error.message.includes('Network request failed')) {
    Alert.alert('No Internet', 'Please check your connection');
  } else {
    Alert.alert('Error', 'Something went wrong');
  }
}
```

#### 2. Token Expiration
```typescript
const response = await fetch(url, options);
if (response.status === 401) {
  await AsyncStorage.clear();
  navigation.navigate('OperatorLogin');
  Alert.alert('Session Expired', 'Please login again');
}
```

#### 3. QR Validation Errors
```typescript
const data = await response.json();

if (!data.valid) {
  if (data.message.includes('not valid for this trip')) {
    Alert.alert('Wrong Trip', 'This QR is for a different trip');
  } else if (data.message.includes('Booking not found')) {
    Alert.alert('Invalid Booking', 'This booking does not exist');
  } else {
    Alert.alert('Invalid QR', data.message);
  }
}
```

#### 4. Photo Upload Errors
```typescript
try {
  const photoUrl = await uploadPhotoToS3(photoBase64, studentId);
} catch (error) {
  // Continue without photo if upload fails
  console.error('Photo upload failed:', error);
  photoUrl = null;
}
```

### Backend Error Responses

**Standard Error Format:**
```json
{
  "statusCode": 400,
  "message": "Descriptive error message"
}
```

**Error Code Mapping:**
| Status Code | Meaning | Frontend Action |
|-------------|---------|-----------------|
| 400 | Bad Request | Show validation error |
| 401 | Unauthorized | Redirect to login |
| 403 | Forbidden | Show "Access denied" |
| 404 | Not Found | Show "Resource not found" |
| 500 | Server Error | Show "Try again later" |

---

## Testing Checklist

### Unit Tests

#### Lambda Functions
```bash
# Test OperatorLoginHandler
- [ ] Valid credentials return token
- [ ] Invalid credentials return 401
- [ ] Suspended account returns 403
- [ ] Missing fields return 400
- [ ] Password hash verification works

# Test GetOperatorTripsHandler
- [ ] Returns trips for valid operator
- [ ] Returns empty array if no trips
- [ ] Invalid token returns 401
- [ ] Filters by today's date correctly

# Test ValidateQRHandler
- [ ] Valid QR marks booking as SCANNED
- [ ] Already scanned QR returns ALREADY_SCANNED
- [ ] Wrong trip QR returns error
- [ ] Invalid QR signature returns error
- [ ] Concurrent scans handled correctly (Redis lock)

# Test GetPassengerListHandler
- [ ] Returns all confirmed bookings
- [ ] Shows correct boarding status
- [ ] Handles empty passenger list
- [ ] Invalid tripId returns empty array

# Test SubmitMisconductReportHandler
- [ ] Report saved to database
- [ ] Photo uploaded to S3
- [ ] S3 URL stored in database
- [ ] Works without photo
- [ ] Invalid student ID returns error

# Test UpdateGPSLocationHandler
- [ ] GPS data published to IoT topic
- [ ] Invalid coordinates rejected
- [ ] Missing tripId returns error
```

### Integration Tests

```bash
# End-to-End Flows
- [ ] Operator login → Trip selection → Start scanning
- [ ] Scan valid QR → Passenger marked as boarded
- [ ] Scan duplicate QR → Warning shown
- [ ] View passenger list → Correct counts displayed
- [ ] Submit report with photo → Photo visible in S3
- [ ] GPS tracking → Student app receives updates

# Database Tests
- [ ] Foreign key constraints enforced
- [ ] Indexes improve query performance
- [ ] Concurrent bookings don't cause deadlocks
- [ ] Transaction rollbacks work correctly

# API Gateway Tests
- [ ] CORS headers present
- [ ] Authorization header validated
- [ ] Rate limiting works
- [ ] Request/response logging enabled
```

### Frontend Tests

```bash
# Screen Tests
- [ ] OperatorLoginScreen validates input
- [ ] BusSelectionScreen loads trips
- [ ] ScannerScreen requests camera permission
- [ ] PassengerListScreen refreshes on focus
- [ ] ReportingModal validates form

# Service Tests
- [ ] GpsTrackingService sends updates every 30s
- [ ] AsyncStorage persists token
- [ ] Network errors handled gracefully
- [ ] Token expiration triggers logout
```

### Performance Tests

```bash
# Load Tests
- [ ] 50 concurrent QR scans handled
- [ ] Passenger list loads in <2 seconds
- [ ] GPS updates don't block UI
- [ ] Photo upload completes in <5 seconds

# Database Performance
- [ ] Trip query with JOINs < 500ms
- [ ] Passenger list query < 300ms
- [ ] QR validation query < 200ms
```

---

## Deployment Steps

### 1. Database Setup
```bash
# Connect to RDS
mysql -h campusbus.xxxxx.rds.amazonaws.com -u admin -p

# Run schema creation
source database-schema.sql

# Insert sample operator
INSERT INTO operators VALUES 
('OP001', 'op101', 'Test Operator', 
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 '+919876543210', 'test@campus.edu', 'ACTIVE', NOW(), NULL);
```

### 2. Lambda Deployment
```bash
# Build JAR
cd BookingSystemBackend
./mvnw clean package

# Deploy each Lambda
aws lambda create-function \
  --function-name OperatorLoginHandler \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT_ID:role/CampusBusOperatorLambdaRole \
  --handler com.campusbus.lambda.OperatorLoginHandler \
  --zip-file fileb://target/booking-system-1.0.0.jar \
  --timeout 30 \
  --memory-size 512 \
  --environment Variables="{DB_HOST=xxx,DB_NAME=campusbus}"
```

### 3. API Gateway Setup
```bash
# Create REST API
aws apigateway create-rest-api --name CampusBusOperatorAPI

# Create resources and methods
# Link to Lambda functions
# Deploy to 'prod' stage
```

### 4. S3 Bucket Setup
```bash
# Create bucket
aws s3 mb s3://campusbus-misconduct-photos

# Set lifecycle policy
aws s3api put-bucket-lifecycle-configuration \
  --bucket campusbus-misconduct-photos \
  --lifecycle-configuration file://lifecycle-policy.json
```

### 5. IoT Core Setup
```bash
# Create IoT policy
aws iot create-policy \
  --policy-name CampusBusGPSPolicy \
  --policy-document file://iot-policy.json

# Attach to Lambda role
```

### 6. Frontend Configuration
```typescript
// Update API base URL in mobile-operator app
export const API_BASE_URL = 'https://xxxxx.execute-api.ap-south-1.amazonaws.com/prod';
```

---

## Monitoring and Logging

### CloudWatch Logs

**Log Groups:**
```
/aws/lambda/OperatorLoginHandler
/aws/lambda/GetOperatorTripsHandler
/aws/lambda/StartTripHandler
/aws/lambda/ValidateQRHandler
/aws/lambda/GetPassengerListHandler
/aws/lambda/SubmitMisconductReportHandler
/aws/lambda/UpdateGPSLocationHandler
```

**Key Metrics to Monitor:**
- Lambda invocation count
- Lambda error rate
- Lambda duration (p50, p95, p99)
- API Gateway 4xx/5xx errors
- RDS connection count
- S3 upload success rate

### Alerts

```yaml
# CloudWatch Alarms
- Lambda Error Rate > 5%
- API Gateway 5xx > 10 requests/minute
- RDS CPU > 80%
- S3 Upload Failures > 5/hour
```

---

## Security Considerations

### 1. Password Security
- Passwords hashed with BCrypt (cost factor 10)
- Never log passwords
- Enforce minimum 8 characters

### 2. Token Security
- JWT tokens signed with HMAC256
- Secret key stored in AWS Secrets Manager
- Tokens expire after 24 hours
- Validate token on every request

### 3. Database Security
- RDS in private subnet
- Security group allows only Lambda access
- Use RDS Proxy for connection pooling
- Enable encryption at rest

### 4. S3 Security
- Bucket not publicly accessible
- Pre-signed URLs for photo access
- Lifecycle policy archives old photos
- Enable versioning for audit trail

### 5. API Security
- Rate limiting: 100 requests/minute per IP
- Request size limit: 10MB
- CORS restricted to mobile app domains
- API keys for additional protection

---

## Troubleshooting Guide

### Issue: Operator can't login
**Check:**
1. Operator exists in database: `SELECT * FROM operators WHERE employee_id = 'op101'`
2. Password hash is correct
3. Operator status is 'ACTIVE'
4. Lambda has database connectivity

### Issue: QR scan fails
**Check:**
1. QR token is valid JWT
2. Trip ID matches current trip
3. Booking exists and is CONFIRMED
4. Redis lock not stuck
5. Database connection available

### Issue: GPS not updating
**Check:**
1. IoT Core topic permissions
2. Lambda can publish to IoT
3. Student app subscribed to correct topic
4. GPS coordinates are valid
5. Network connectivity

### Issue: Photo upload fails
**Check:**
1. S3 bucket exists
2. Lambda has PutObject permission
3. Photo size < 5MB
4. Base64 encoding is correct
5. Bucket not full

---

## Cross-Reference Summary

### Shared Components with Student App

| Component | Student App | Operator App | Shared? |
|-----------|-------------|--------------|---------|
| Database Tables | students, trips, bookings | operators, trip_assignments, misconduct_reports | Partially |
| ValidateQRHandler | ❌ | ✅ | Yes (reused) |
| QRCodeGenerator | ✅ | ❌ | Yes (utility) |
| AuthTokenUtil | ✅ | ✅ | Yes (extended) |
| AWS IoT Core | ✅ (subscribe) | ✅ (publish) | Yes |
| S3 Bucket | ❌ | ✅ | No |

### Data Flow Between Apps

```
Student App                    Backend                    Operator App
    |                             |                             |
    | Book Trip                   |                             |
    |---------------------------→ |                             |
    |                             | Create booking              |
    |                             | Generate QR                 |
    | ←---------------------------|                             |
    | Display QR                  |                             |
    |                             |                             |
    |                             |                             | Scan QR
    |                             | ←---------------------------|
    |                             | Validate & Mark SCANNED     |
    |                             |---------------------------→ |
    |                             |                             | Show Success
    |                             |                             |
    |                             |                             | Send GPS
    |                             | ←---------------------------|
    |                             | Publish to IoT              |
    | ←---------------------------|                             |
    | Update Map                  |                             |
```

---

## Conclusion

This backend integration guide provides complete implementation details for the Mobile Operator App. All Lambda functions, database schemas, API endpoints, and AWS services are documented with code examples and cross-references to the existing student app implementation.

**Key Takeaways:**
- 7 Lambda functions required (6 new + 1 reused)
- 3 new database tables
- Integration with S3, IoT Core, and RDS
- Separate authentication from student app
- Real-time GPS tracking via IoT Core
- Photo upload for misconduct reports

**Next Steps:**
1. Create database tables
2. Deploy Lambda functions
3. Configure API Gateway
4. Set up S3 bucket and IoT Core
5. Update frontend with API URLs
6. Test end-to-end flows
7. Monitor and optimize performance
