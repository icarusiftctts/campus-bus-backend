-- Database Schema Update for Actual Bus Routes
-- Add new columns to trips table

ALTER TABLE trips 
ADD COLUMN IF NOT EXISTS destination VARCHAR(50) AFTER route,
ADD COLUMN IF NOT EXISTS bus_number VARCHAR(10) AFTER destination,
ADD COLUMN IF NOT EXISTS day_type VARCHAR(10) DEFAULT 'WEEKDAY' AFTER status;

-- Add index for efficient querying
CREATE INDEX IF NOT EXISTS idx_trips_route_date_time_day ON trips(route, trip_date, departure_time, day_type);

-- Update existing trips to have default day_type
UPDATE trips SET day_type = 'WEEKDAY' WHERE day_type IS NULL;
