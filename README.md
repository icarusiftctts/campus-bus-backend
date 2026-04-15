# Campus Bus Backend

Serverless backend for campus bus booking, operator workflows, and real-time tracking. Built with Spring Boot on AWS Lambda.

---

## Features
- **Student**
  - Book trips (confirm/waitlist)
  - QR-based boarding
  - Live tracking

- **Operator**
  - Trip management
  - QR scanning (single-use, secure)
  - Misconduct reporting (with photo)
  - GPS updates

- **System**
  - Dual auth (Cognito + JWT)
  - Penalty system (auto-block)
  - Real-time updates (IoT Core)

---

## Architecture
- API Gateway → Lambda (stateless)
- RDS MySQL (via RDS Proxy)
- Redis (QR locking)
- S3 (photos)
- IoT Core (GPS)

---

## Tech Stack
- Java 17, Spring Boot 3.5.6  
- MySQL (RDS), Hibernate JPA  
- AWS: Lambda, API Gateway, Cognito, S3, IoT Core  
- Redis, JWT, BCrypt  

---

## Modules
- **Entities**: Student, Operator, Trip, Booking, Assignment, Report  
- **Handlers**: Auth, Booking, QR, GPS, Reports  
- **Utils**: JWT, QR  

---

## Database
- `students` → profile + penalties  
- `operators` → credentials  
- `trips` → schedules  
- `bookings` → reservations + QR  
- `trip_assignments` → operator mapping  
- `misconduct_reports` → incidents  

---

## Auth
- Students → Cognito + JWT (7d)  
- Operators → DB + JWT (24h)  

---

## Flows
**Booking**
1. Login → fetch trips  
2. Book → CONFIRMED / WAITLIST  
3. QR → scan → SCANNED  

**Operator**
1. Login → trips  
2. Scan QR → validate  
3. Report / GPS update  

---

## Security
- JWT (HMAC-256)
- BCrypt passwords
- QR: signed, single-use, trip-specific
- Redis locks (no double scan)
- Domain restriction (@lnmiit.ac.in)

---

## Performance
- Stateless Lambda scaling  
- RDS Proxy pooling  
- Lazy init (cold start optimization)  
- Minimal logging  

---

## Build
```bash
mvn clean package
```

Deploy generated JAR to AWS Lambda.

## Environment Variables
- **DB**: `RDS_PROXY_ENDPOINT`, `DB_USERNAME`, `DB_PASSWORD`  
- **Auth**: `COGNITO_*`, `AUTH_SECRET_KEY`, `QR_SECRET_KEY`  
- **Redis**: `REDIS_ENDPOINT`  
- **S3**: `S3_BUCKET_NAME`, `AWS_REGION`  

## Status
Production-ready serverless backend for campus transport systems.
