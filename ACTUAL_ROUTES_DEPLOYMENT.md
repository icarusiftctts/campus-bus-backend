# Actual Bus Routes Implementation - Deployment Guide

## Overview
This update implements the actual LNMIIT bus timetable with real routes, destinations, bus numbers, and separate weekday/weekend schedules.

## Changes Made

### 1. Database Schema Updates
**New columns added to `trips` table:**
- `destination` VARCHAR(50) - Destination name (Raja Park, Ajmeri Gate, Transport Nagar)
- `bus_number` VARCHAR(10) - Bus identifier (1, 2, 3, 4)
- `day_type` VARCHAR(10) - Schedule type (WEEKDAY, WEEKEND)

### 2. Backend Updates
**Modified files:**
- `Trip.java` - Added destination, busNumber, dayType fields with getters/setters
- `TripRepository.java` - Updated queries to filter by dayType and include new fields
- `GetAvailableTripsHandler.java` - Auto-detects weekday/weekend and filters accordingly
- `CreateTripHandler.java` - Supports new fields when creating trips

### 3. Route Data
**Weekday Routes (Monday-Friday):**
- 9 Campus → City trips (06:00 AM to 07:30 PM)
- 9 City → Campus trips (07:00 AM to 09:00 PM)

**Weekend Routes (Saturday-Sunday):**
- 7 Campus → City trips (07:00 AM to 06:00 PM)
- 7 City → Campus trips (08:00 AM to 09:00 PM)

**Destinations:**
- Raja Park
- Ajmeri Gate
- Transport Nagar

## Deployment Steps

### Step 1: Backup Current Database
```bash
mysqldump -h your-rds-endpoint -u admin -p campusbus > backup_before_routes.sql
```

### Step 2: Apply Schema Changes
```bash
mysql -h your-rds-endpoint -u admin -p campusbus < database-schema-update.sql
```

### Step 3: Seed Actual Routes
```bash
mysql -h your-rds-endpoint -u admin -p campusbus < seed-actual-routes.sql
```

### Step 4: Rebuild and Deploy Backend
```bash
cd BookingSystemBackend
./mvnw clean package
aws lambda update-function-code \
  --function-name GetAvailableTripsFunction \
  --zip-file fileb://target/booking-system-0.0.1-SNAPSHOT.jar

aws lambda update-function-code \
  --function-name CreateTripFunction \
  --zip-file fileb://target/booking-system-0.0.1-SNAPSHOT.jar
```

### Step 5: Verify Deployment
```bash
# Test weekday routes
curl -X GET "https://your-api-gateway/api/trips/available?route=CAMPUS_TO_CITY&date=2025-01-13" \
  -H "Authorization: Bearer YOUR_TOKEN"

# Test weekend routes
curl -X GET "https://your-api-gateway/api/trips/available?route=CAMPUS_TO_CITY&date=2025-01-18" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## Verification Queries

### Check schema updates
```sql
DESCRIBE trips;
```

### Verify route data
```sql
SELECT 
    day_type,
    route,
    COUNT(*) as trip_count,
    MIN(departure_time) as first_trip,
    MAX(departure_time) as last_trip
FROM trips
GROUP BY day_type, route
ORDER BY day_type, route;
```

### View all routes sorted by time
```sql
SELECT 
    day_type,
    route,
    destination,
    bus_number,
    TIME_FORMAT(departure_time, '%h:%i %p') as time
FROM trips
WHERE trip_date = CURDATE()
ORDER BY day_type, route, departure_time;
```

## API Response Changes

### Before (Old Format)
```json
{
  "tripId": "T12AB34CD",
  "route": "CAMPUS_TO_CITY",
  "departureTime": "08:30:00",
  "capacity": 35
}
```

### After (New Format)
```json
{
  "tripId": "TRIP_WD_0830_1_C2C",
  "route": "CAMPUS_TO_CITY",
  "destination": "Raja Park",
  "busNumber": "1",
  "departureTime": "08:30:00",
  "dayType": "WEEKDAY",
  "capacity": 35,
  "bookedCount": 28,
  "availableSeats": 2,
  "waitlistCount": 5
}
```

## Frontend Updates Needed

### Display destination in trip cards
```typescript
// Before
<Text>{trip.time}</Text>

// After
<Text>{trip.time} → {trip.destination}</Text>
<Text>Bus #{trip.busNumber}</Text>
```

### No code changes required for day type filtering
The backend automatically detects weekday/weekend based on the selected date.

## Rollback Plan

If issues occur, restore from backup:
```bash
mysql -h your-rds-endpoint -u admin -p campusbus < backup_before_routes.sql
```

Then redeploy previous Lambda version:
```bash
aws lambda update-function-code \
  --function-name GetAvailableTripsFunction \
  --s3-bucket your-backup-bucket \
  --s3-key previous-version.jar
```

## Testing Checklist

- [ ] Schema update applied successfully
- [ ] 18 weekday routes inserted (9 each direction)
- [ ] 14 weekend routes inserted (7 each direction)
- [ ] GetAvailableTrips returns correct routes for weekdays
- [ ] GetAvailableTrips returns correct routes for weekends
- [ ] Routes sorted by departure time
- [ ] Destination and bus number displayed correctly
- [ ] Existing bookings still work
- [ ] QR code validation still works
- [ ] Operator app shows correct trip details

## Notes

- All routes use default capacity of 35 seats with 5 faculty reserved
- Trip IDs follow format: `TRIP_{WD|WE}_{TIME}_{BUS#}_{C2C|CT2C}`
- System automatically determines weekday/weekend based on date
- Routes are sorted by departure time in all queries
- Existing booking logic unchanged - fully backward compatible

## Support

For issues or questions:
1. Check CloudWatch logs for Lambda errors
2. Verify database connection and schema
3. Test API endpoints with Postman/curl
4. Review frontend console for API response format changes
