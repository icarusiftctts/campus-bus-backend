-- Seed Actual Bus Routes from Timetable
-- Effective from September 10, 2025

-- Clear existing test data
DELETE FROM bookings;
DELETE FROM trips;

-- WEEKDAY ROUTES (Monday to Friday)
-- Campus to City (LNMIIT → City destinations) - Sorted by time
INSERT INTO trips (trip_id, route, destination, bus_number, trip_date, departure_time, capacity, faculty_reserved, status, day_type) VALUES
('TRIP_WD_0600_1_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '1', CURDATE(), '06:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_0700_2_C2C', 'CAMPUS_TO_CITY', 'Ajmeri Gate', '2', CURDATE(), '07:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_0700_3_C2C', 'CAMPUS_TO_CITY', 'Ajmeri Gate', '3', CURDATE(), '07:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1000_4_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '4', CURDATE(), '10:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1400_2_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '2', CURDATE(), '14:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1630_3_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '3', CURDATE(), '16:30:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1805_1_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '1', CURDATE(), '18:05:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1805_2_C2C', 'CAMPUS_TO_CITY', 'Ajmeri Gate', '2', CURDATE(), '18:05:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1930_3_C2C', 'CAMPUS_TO_CITY', 'Ajmeri Gate', '3', CURDATE(), '19:30:00', 35, 5, 'ACTIVE', 'WEEKDAY');

-- City to Campus (City destinations → LNMIIT) - Sorted by time
INSERT INTO trips (trip_id, route, destination, bus_number, trip_date, departure_time, capacity, faculty_reserved, status, day_type) VALUES
('TRIP_WD_0700_1_CT2C', 'CITY_TO_CAMPUS', 'Raja Park', '1', CURDATE(), '07:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_0800_2_CT2C', 'CITY_TO_CAMPUS', 'Ajmeri Gate', '2', CURDATE(), '08:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_0800_3_CT2C', 'CITY_TO_CAMPUS', 'Ajmeri Gate', '3', CURDATE(), '08:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1100_4_CT2C', 'CITY_TO_CAMPUS', 'Raja Park', '4', CURDATE(), '11:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1600_2_CT2C', 'CITY_TO_CAMPUS', 'Transport Nagar', '2', CURDATE(), '16:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_1730_3_CT2C', 'CITY_TO_CAMPUS', 'Transport Nagar', '3', CURDATE(), '17:30:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_2015_2_CT2C', 'CITY_TO_CAMPUS', 'Ajmeri Gate', '2', CURDATE(), '20:15:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_2100_1_CT2C', 'CITY_TO_CAMPUS', 'Transport Nagar', '1', CURDATE(), '21:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
('TRIP_WD_2100_3_CT2C', 'CITY_TO_CAMPUS', 'Ajmeri Gate', '3', CURDATE(), '21:00:00', 35, 5, 'ACTIVE', 'WEEKDAY');

-- WEEKEND ROUTES (Saturday, Sunday & Holidays)
-- Campus to City - Sorted by time
INSERT INTO trips (trip_id, route, destination, bus_number, trip_date, departure_time, capacity, faculty_reserved, status, day_type) VALUES
('TRIP_WE_0700_1_C2C', 'CAMPUS_TO_CITY', 'Ajmeri Gate', '1', CURDATE(), '07:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1000_2_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '2', CURDATE(), '10:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1300_3_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '3', CURDATE(), '13:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1600_2_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '2', CURDATE(), '16:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1630_3_C2C', 'CAMPUS_TO_CITY', 'Ajmeri Gate', '3', CURDATE(), '16:30:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1700_1_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '1', CURDATE(), '17:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1800_2_C2C', 'CAMPUS_TO_CITY', 'Raja Park', '2', CURDATE(), '18:00:00', 35, 5, 'ACTIVE', 'WEEKEND');

-- City to Campus - Sorted by time
INSERT INTO trips (trip_id, route, destination, bus_number, trip_date, departure_time, capacity, faculty_reserved, status, day_type) VALUES
('TRIP_WE_0800_1_CT2C', 'CITY_TO_CAMPUS', 'Ajmeri Gate', '1', CURDATE(), '08:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1200_2_CT2C', 'CITY_TO_CAMPUS', 'Raja Park', '2', CURDATE(), '12:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1500_3_CT2C', 'CITY_TO_CAMPUS', 'Transport Nagar', '3', CURDATE(), '15:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_1715_2_CT2C', 'CITY_TO_CAMPUS', 'Transport Nagar', '2', CURDATE(), '17:15:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_2015_3_CT2C', 'CITY_TO_CAMPUS', 'Ajmeri Gate', '3', CURDATE(), '20:15:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_2100_1_CT2C', 'CITY_TO_CAMPUS', 'Transport Nagar', '1', CURDATE(), '21:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
('TRIP_WE_2100_2_CT2C', 'CITY_TO_CAMPUS', 'Transport Nagar', '2', CURDATE(), '21:00:00', 35, 5, 'ACTIVE', 'WEEKEND');

-- Verify data
SELECT 
    day_type,
    route,
    COUNT(*) as trip_count,
    MIN(departure_time) as first_trip,
    MAX(departure_time) as last_trip
FROM trips
GROUP BY day_type, route
ORDER BY day_type, route;
