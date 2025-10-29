# API Gateway Setup Guide

## API Endpoints Structure

### Base URL
```
https://your-api-id.execute-api.ap-south-1.amazonaws.com/dev
```

## Authentication Endpoints

### 1. Register User
- **Method**: POST
- **Path**: `/api/auth/register`
- **Authorization**: None (public)
- **Lambda**: RegisterUserHandler
- **Request Body**:
```json
{
  "email": "student@college.edu",
  "name": "John Doe",
  "password": "securePassword123",
  "room": "A-101",
  "phone": "+91-9876543210"
}
```

### 2. Login
- **Method**: POST
- **Path**: `/api/auth/login`
- **Authorization**: None (public)
- **Lambda**: LoginHandler
- **Request Body**:
```json
{
  "email": "student@college.edu",
  "password": "securePassword123"
}
```

## Trip Management Endpoints

### 3. Get Available Trips
- **Method**: GET
- **Path**: `/api/trips/available`
- **Authorization**: Required (Bearer token)
- **Lambda**: GetAvailableTripsHandler
- **Query Parameters**:
  - `route`: "CAMPUS_TO_CITY" or "CITY_TO_CAMPUS"
  - `tripDate`: "YYYY-MM-DD"

### 4. Create Trip (Admin)
- **Method**: POST
- **Path**: `/api/trips`
- **Authorization**: Required (Admin token)
- **Lambda**: CreateTripHandler
- **Request Body**:
```json
{
  "route": "CAMPUS_TO_CITY",
  "tripDate": "2024-10-15",
  "departureTime": "08:30",
  "capacity": 35,
  "facultyReserved": 5
}
```

## Booking Management Endpoints

### 5. Book Trip
- **Method**: POST
- **Path**: `/api/bookings`
- **Authorization**: Required (Bearer token)
- **Lambda**: BookTripHandler
- **Request Body**:
```json
{
  "tripId": "T12AB34CD"
}
```

### 6. Cancel Booking
- **Method**: DELETE
- **Path**: `/api/bookings/{bookingId}`
- **Authorization**: Required (Bearer token)
- **Lambda**: CancelBookingHandler
- **Path Parameters**: `bookingId`

### 7. Get Student Booking History
- **Method**: GET
- **Path**: `/api/bookings/history`
- **Authorization**: Required (Bearer token)
- **Lambda**: GetStudentBookingsHandler
- **Response**:
```json
{
  "bookings": [
    {
      "bookingId": "B12AB34CD",
      "tripId": "T12AB34CD",
      "status": "SCANNED",
      "bookedAt": "2024-10-15T08:30:00",
      "route": "CAMPUS_TO_CITY",
      "tripDate": "2024-10-15",
      "departureTime": "08:30"
    }
  ]
}
```

## Profile Endpoints

### 8. Get User Profile
- **Method**: GET
- **Path**: `/api/profile`
- **Authorization**: Required (Bearer token)
- **Lambda**: GetUserProfileHandler
- **Response**:
```json
{
  "studentId": "S12AB34CD",
  "email": "student@college.edu",
  "name": "John Doe",
  "room": "A-101",
  "phone": "+91-9876543210",
  "penaltyCount": 0,
  "isBlocked": false,
  "profileComplete": true,
  "activeBookings": []
}
```

## QR Code Endpoints

### 9. Validate QR Code
- **Method**: POST
- **Path**: `/api/qr/validate`
- **Authorization**: Required (Operator token)
- **Lambda**: ValidateQRHandler
- **Request Body**:
```json
{
  "qrToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tripId": "T12AB34CD"
}
```

## CORS Configuration

```json
{
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Content-Type,Authorization",
  "Access-Control-Allow-Methods": "GET,POST,DELETE,OPTIONS"
}
```

## Lambda Integration Settings

For each endpoint:
- **Integration Type**: Lambda Function
- **Lambda Proxy Integration**: Enabled
- **Use Default Timeout**: Enabled

## Deployment Stages

- **dev**: Development environment
- **prod**: Production environment

## Flutter Integration Headers

```dart
Map<String, String> headers = {
  'Content-Type': 'application/json',
  'Authorization': 'Bearer $authToken',
};
```

## Error Response Format

All endpoints return errors in this format:
```json
{
  "message": "Error description"
}
```

## Success Response Formats

Vary by endpoint - see individual handler comments for details.