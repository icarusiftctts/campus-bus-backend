# 🔥 Backend MVP Checklist - Week 1 (Auth + Booking Core) - **MySQL Edition**

## **Day 1: AWS Foundation + Database Setup**

### ☐ AWS Account & Services Setup

- [ ]  Create/configure AWS account with Mumbai region (ap-south-1)
- [ ]  Set up AWS CLI with appropriate credentials
- [ ]  Create IAM role for Lambda execution with policies:
    - `AWSLambdaBasicExecutionRole`
    - RDS connect permissions
    - Cognito admin permissions
    - VPC access for RDS connectivity

### ☐ MySQL Database Setup (RDS)

- [ ]  **Create RDS MySQL instance**:
    - Engine: MySQL 8.0
    - Instance class: db.t3.micro (free tier)
    - Multi-AZ deployment: No (for cost saving)
    - Storage: 20GB GP2
    - Database name: `campusbus`
- [ ]  **Set up RDS Proxy** (critical for Lambda connection pooling):
    - Target: MySQL RDS instance
    - Auth: IAM + database credentials
    - Max connections: 100
- [ ]  **Create VPC Security Groups**:
    - RDS security group: Allow MySQL (3306) from Lambda security group
    - Lambda security group: Allow outbound to RDS

### ☐ Database Schema Design

- [ ]  **Create normalized tables**:

```sql
-- Core entity tables
CREATE TABLE students (
    student_id VARCHAR(50) PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    room VARCHAR(20),
    phone VARCHAR(15),
    penalty_count INT DEFAULT 0,
    blocked_until DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trips (
    trip_id VARCHAR(50) PRIMARY KEY,
    route ENUM('CAMPUS_TO_CITY', 'CITY_TO_CAMPUS') NOT NULL,
    trip_date DATE NOT NULL,
    departure_time TIME NOT NULL,
    capacity INT NOT NULL DEFAULT 35,
    faculty_reserved INT DEFAULT 5,
    status ENUM('ACTIVE', 'CANCELLED', 'COMPLETED') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_route_date_time (route, trip_date, departure_time)
);

CREATE TABLE bookings (
    booking_id VARCHAR(50) PRIMARY KEY,
    student_id VARCHAR(50) NOT NULL,
    trip_id VARCHAR(50) NOT NULL,
    status ENUM('CONFIRMED', 'WAITLIST', 'CANCELLED', 'SCANNED') DEFAULT 'CONFIRMED',
    qr_token TEXT,
    booked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    scanned_at TIMESTAMP NULL,
    waitlist_position INT NULL,
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
    INDEX idx_trip_status (trip_id, status),
    INDEX idx_student_bookings (student_id, booked_at DESC)
);

CREATE TABLE penalties (
    penalty_id VARCHAR(50) PRIMARY KEY,
    student_id VARCHAR(50) NOT NULL,
    trip_id VARCHAR(50),
    penalty_type ENUM('NO_SHOW', 'LATE_CANCEL') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(student_id)
);
```

### ☐ Project Structure

- [ ]  Initialize Java Spring Boot project with **MySQL dependencies**:
    - `spring-boot-starter-web`
    - `spring-boot-starter-data-jpa` (**NEW** - for MySQL ORM)
    - `mysql-connector-j` (**NEW** - MySQL driver)
    - `spring-boot-starter-jdbc` (**NEW** - for JdbcTemplate)
    - `aws-lambda-java-core`
    - `aws-lambda-java-events`
    - `spring-cloud-function-adapter-aws`
    - `HikariCP` (**NEW** - connection pooling)

---

## **Day 2: Connection Management + Authentication**

### ☐ Database Connection Setup

- [ ]  **Configure connection pooling for Lambda**:

```java
@Configuration
public class DatabaseConfig {
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + RDS_PROXY_ENDPOINT + ":3306/campusbus");
        config.setUsername(System.getenv("DB_USERNAME"));
        config.setPassword(System.getenv("DB_PASSWORD"));
        config.setMaximumPoolSize(1); // CRITICAL: Max 1 per Lambda instance
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(60000);
        return new HikariDataSource(config);
    }
}
```

- [ ]  **Create JPA Entity classes**:

```java
@Entity
@Table(name = "students")
public class Student {
    @Id
    private String studentId;
    private String email;
    private String name;
    // ... other fields
}
```

### ☐ AWS Cognito Setup (Unchanged)

- [ ]  Create Cognito User Pool with college email domain restriction
- [ ]  Configure User Pool with required attributes
- [ ]  Create App Client for mobile access

### ☐ Auth Lambda Functions (MySQL Integration)

- [ ]  **Function 1**: `RegisterUser`
    - Input: email, password, studentId, room, phone
    - **NEW**: Insert into `students` table using JPA repository
    - Handle MySQL constraints (unique email)
- [ ]  **Function 2**: `LoginUser` (unchanged from original)
- [ ]  **Function 3**: `GetUserProfile`
    - **NEW**: Query `students` table with JOIN to get penalty info

```java
@Query("SELECT s FROM Student s LEFT JOIN FETCH s.penalties WHERE s.studentId = ?1")
Student findStudentWithPenalties(String studentId);
```

---

## **Day 3: Trip Management with SQL**

### ☐ Trip Repository Layer

- [ ]  **Create Trip JPA Repository**:

```java
@Repository
public interface TripRepository extends JpaRepository<Trip, String> {
    @Query("SELECT t FROM Trip t WHERE t.route = ?1 AND t.tripDate = ?2 ORDER BY t.departureTime")
    List<Trip> findByRouteAndDate(String route, LocalDate date);
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.tripId = ?1 AND b.status IN ('CONFIRMED', 'SCANNED')")
    int countConfirmedBookings(String tripId);
}
```

### ☐ Trip Lambda Functions

- [ ]  **Function 4**: `CreateTrip` (Admin only)
    - **NEW**: Use JPA to insert trip record
    - Handle MySQL auto-generated IDs vs UUIDs
- [ ]  **Function 5**: `GetAvailableTrips`
    - **NEW**: Complex SQL query with JOINs

```sql
SELECT t.*, 
       COUNT(CASE WHEN b.status IN ('CONFIRMED', 'SCANNED') THEN 1 END) as booked_count,
       COUNT(CASE WHEN b.status = 'WAITLIST' THEN 1 END) as waitlist_count
FROM trips t 
LEFT JOIN bookings b ON t.trip_id = b.trip_id 
WHERE t.trip_date = ? AND t.route = ? AND t.status = 'ACTIVE'
GROUP BY t.trip_id
```

- [ ]  **Function 6**: `GetTripDetails` with passenger list JOIN

### ☐ Seed Initial Data

- [ ]  Create SQL script to populate sample trips
- [ ]  Use MySQL INSERT statements instead of DynamoDB putItem

---

## **Day 4: Booking System with MySQL Transactions**

### ☐ ElastiCache Redis Setup (Still Required)

- [ ]  Create ElastiCache Redis cluster for distributed locking
- [ ]  **MORE CRITICAL NOW**: Redis prevents MySQL deadlocks during concurrent booking

### ☐ Booking System with Transactions

- [ ]  **Function 7**: `BookTrip` - **MAJOR CHANGES**:

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public BookingResult bookTrip(String studentId, String tripId) {
    // 1. Acquire Redis lock (prevent concurrent booking same trip)
    String lockKey = "booking:" + tripId;
    if (!redisTemplate.opsForValue().setIfAbsent(lockKey, studentId, Duration.ofSeconds(30))) {
        return BookingResult.CONCURRENT_REQUEST;
    }
    
    try {
        // 2. Check student penalty status with SQL
        Student student = studentRepository.findById(studentId);
        if (student.isBlocked()) {
            return BookingResult.STUDENT_BLOCKED;
        }
        
        // 3. Count current bookings with aggregate query
        int confirmedCount = bookingRepository.countConfirmedBookings(tripId);
        Trip trip = tripRepository.findById(tripId);
        
        // 4. Determine booking status
        if (confirmedCount < (trip.getCapacity() - trip.getFacultyReserved())) {
            // Create confirmed booking
            Booking booking = new Booking(UUID.randomUUID().toString(), studentId, tripId, "CONFIRMED");
            [bookingRepository.save](http://bookingRepository.save)(booking);
            return BookingResult.confirmed(booking);
        } else {
            // Add to waitlist with position
            int waitlistPosition = bookingRepository.countWaitlistBookings(tripId) + 1;
            Booking booking = new Booking(UUID.randomUUID().toString(), studentId, tripId, "WAITLIST");
            booking.setWaitlistPosition(waitlistPosition);
            [bookingRepository.save](http://bookingRepository.save)(booking);
            return BookingResult.waitlisted(booking, waitlistPosition);
        }
    } finally {
        redisTemplate.delete(lockKey);
    }
}
```

### ☐ Waitlist Management

- [ ]  **Function 8**: `CancelBooking`
    - **NEW**: Use SQL UPDATE + subquery to promote waitlist

```sql
-- Promote next waitlisted user
UPDATE bookings 
SET status = 'CONFIRMED', waitlist_position = NULL 
WHERE trip_id = ? AND status = 'WAITLIST' 
ORDER BY booked_at LIMIT 1;

-- Update other waitlist positions  
UPDATE bookings 
SET waitlist_position = waitlist_position - 1 
WHERE trip_id = ? AND status = 'WAITLIST';
```

---

## **Day 5: QR Code System (Minimal Changes)**

### ☐ QR Code Generation (Same Logic)

- [ ]  JWT signing with HMAC256 (unchanged)
- [ ]  **NEW**: Store QR token in MySQL `bookings.qr_token` column

### ☐ QR Validation Functions

- [ ]  **Function 10**: `GenerateQR`
    - **NEW**: UPDATE SQL query instead of DynamoDB putItem
- [ ]  **Function 11**: `ValidateQR`
    - **NEW**: Use MySQL UPDATE with WHERE conditions

```java
@Modifying
@Query("UPDATE Booking SET status = 'SCANNED', scannedAt = CURRENT_TIMESTAMP WHERE bookingId = ?1 AND tripId = ?2")
int markBookingScanned(String bookingId, String tripId);
```

---

## **Day 6: API Gateway + Performance Optimization**

### ☐ API Gateway Setup (Unchanged)

- [ ]  Create REST API with Cognito authorizer
- [ ]  Same endpoint structure as original plan

### ☐ **NEW**: Database Performance Optimization

- [ ]  **Add database indexes** for common queries:

```sql
-- Critical indexes for performance
CREATE INDEX idx_bookings_trip_status ON bookings(trip_id, status);
CREATE INDEX idx_bookings_student_date ON bookings(student_id, booked_at);
CREATE INDEX idx_trips_route_date ON trips(route, trip_date, departure_time);
```

- [ ]  **Query optimization**: Use JPA @Query with fetch joins to avoid N+1 problems
- [ ]  **Connection monitoring**: Add CloudWatch metrics for RDS connections

### ☐ **NEW**: Caching Strategy

- [ ]  **Cache trip availability** in Redis (5-minute TTL)
- [ ]  **Cache user profiles** to reduce database hits

```java
@Cacheable(value = "trip-availability", key = "#tripId")
public TripAvailability getTripAvailability(String tripId) {
    // Expensive JOIN query cached for 5 minutes
}
```

---

## **Day 7: Testing + Database Migration Scripts**

### ☐ **NEW**: Database Testing

- [ ]  **Connection pool testing**: Verify max 100 concurrent Lambda connections
- [ ]  **Transaction testing**: Simulate concurrent bookings, verify no double-booking
- [ ]  **Performance testing**: Query response time under load
- [ ]  **Failover testing**: Test RDS Proxy connection recovery

### ☐ **NEW**: Data Migration Scripts

- [ ]  Create scripts for initial data seeding
- [ ]  Create backup/restore procedures
- [ ]  Document database schema changes for production deployment

### ☐ Integration Testing (Updated)

- [ ]  Test complete user flow with MySQL backend
- [ ]  **NEW**: Test connection exhaustion scenarios
- [ ]  **NEW**: Test database transaction rollbacks
- [ ]  Test API response times (target: <2 seconds instead of <500ms)

---

## **📋 Success Criteria for Week 1 MVP (MySQL Edition)**

By Friday evening, you should have:

- ✅ **RDS MySQL + RDS Proxy** configured and accessible from Lambda
- ✅ **Normalized database schema** with proper foreign keys and indexes
- ✅ **Connection pooling** working (max 1 connection per Lambda instance)
- ✅ Students can register/login (user data stored in MySQL)
- ✅ Students can book seats with **SQL transaction protection**
- ✅ **Waitlist promotion** works with SQL UPDATE queries
- ✅ QR codes stored in MySQL and validate correctly
- ✅ **Redis distributed locking** prevents booking conflicts
- ✅ All APIs tested with **acceptable response times** (<2 seconds)

**Total Lambda Functions**: 11 functions (same as original)