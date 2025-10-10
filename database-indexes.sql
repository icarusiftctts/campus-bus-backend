-- Performance Indexes for Campus Bus Booking System
-- Execute these on RDS MySQL after table creation

-- Index for booking queries by student and date
CREATE INDEX idx_bookings_student_date ON bookings(student_id, booked_at);

-- Index for trip queries by route and date
CREATE INDEX idx_trips_route_date ON trips(route, trip_date, departure_time);

-- Index for booking counts by trip and status (most critical for availability checks)
CREATE INDEX idx_bookings_trip_status ON bookings(trip_id, status);

-- Index for waitlist position queries
CREATE INDEX idx_bookings_waitlist ON bookings(trip_id, status, waitlist_position);

-- Index for penalty queries
CREATE INDEX idx_penalties_student ON penalties(student_id, created_at);