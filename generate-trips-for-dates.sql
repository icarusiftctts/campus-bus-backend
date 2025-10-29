-- Generate trips for next 30 days based on day type
-- This script creates trips for the next month automatically

DELIMITER $$

CREATE PROCEDURE GenerateTripsForDateRange(
    IN start_date DATE,
    IN end_date DATE
)
BEGIN
    DECLARE current_date DATE;
    DECLARE day_of_week INT;
    DECLARE current_day_type VARCHAR(10);
    
    SET current_date = start_date;
    
    WHILE current_date <= end_date DO
        -- Determine day type (1=Monday, 7=Sunday)
        SET day_of_week = DAYOFWEEK(current_date);
        SET current_day_type = IF(day_of_week IN (1, 7), 'WEEKEND', 'WEEKDAY');
        
        -- Insert weekday routes
        IF current_day_type = 'WEEKDAY' THEN
            -- Campus to City
            INSERT INTO trips (trip_id, route, destination, bus_number, trip_date, departure_time, capacity, faculty_reserved, status, day_type) VALUES
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_0600_1_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '1', current_date, '06:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_0700_2_C2C'), 'CAMPUS_TO_CITY', 'Ajmeri Gate', '2', current_date, '07:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_0700_3_C2C'), 'CAMPUS_TO_CITY', 'Ajmeri Gate', '3', current_date, '07:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1000_4_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '4', current_date, '10:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1400_2_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '2', current_date, '14:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1630_3_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '3', current_date, '16:30:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1805_1_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '1', current_date, '18:05:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1805_2_C2C'), 'CAMPUS_TO_CITY', 'Ajmeri Gate', '2', current_date, '18:05:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1930_3_C2C'), 'CAMPUS_TO_CITY', 'Ajmeri Gate', '3', current_date, '19:30:00', 35, 5, 'ACTIVE', 'WEEKDAY');
            
            -- City to Campus
            INSERT INTO trips (trip_id, route, destination, bus_number, trip_date, departure_time, capacity, faculty_reserved, status, day_type) VALUES
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_0700_1_CT2C'), 'CITY_TO_CAMPUS', 'Raja Park', '1', current_date, '07:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_0800_2_CT2C'), 'CITY_TO_CAMPUS', 'Ajmeri Gate', '2', current_date, '08:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_0800_3_CT2C'), 'CITY_TO_CAMPUS', 'Ajmeri Gate', '3', current_date, '08:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1100_4_CT2C'), 'CITY_TO_CAMPUS', 'Raja Park', '4', current_date, '11:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1600_2_CT2C'), 'CITY_TO_CAMPUS', 'Transport Nagar', '2', current_date, '16:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1730_3_CT2C'), 'CITY_TO_CAMPUS', 'Transport Nagar', '3', current_date, '17:30:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_2015_2_CT2C'), 'CITY_TO_CAMPUS', 'Ajmeri Gate', '2', current_date, '20:15:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_2100_1_CT2C'), 'CITY_TO_CAMPUS', 'Transport Nagar', '1', current_date, '21:00:00', 35, 5, 'ACTIVE', 'WEEKDAY'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_2100_3_CT2C'), 'CITY_TO_CAMPUS', 'Ajmeri Gate', '3', current_date, '21:00:00', 35, 5, 'ACTIVE', 'WEEKDAY');
        
        -- Insert weekend routes
        ELSE
            -- Campus to City
            INSERT INTO trips (trip_id, route, destination, bus_number, trip_date, departure_time, capacity, faculty_reserved, status, day_type) VALUES
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_0700_1_C2C'), 'CAMPUS_TO_CITY', 'Ajmeri Gate', '1', current_date, '07:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1000_2_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '2', current_date, '10:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1300_3_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '3', current_date, '13:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1600_2_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '2', current_date, '16:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1630_3_C2C'), 'CAMPUS_TO_CITY', 'Ajmeri Gate', '3', current_date, '16:30:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1700_1_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '1', current_date, '17:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1800_2_C2C'), 'CAMPUS_TO_CITY', 'Raja Park', '2', current_date, '18:00:00', 35, 5, 'ACTIVE', 'WEEKEND');
            
            -- City to Campus
            INSERT INTO trips (trip_id, route, destination, bus_number, trip_date, departure_time, capacity, faculty_reserved, status, day_type) VALUES
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_0800_1_CT2C'), 'CITY_TO_CAMPUS', 'Ajmeri Gate', '1', current_date, '08:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1200_2_CT2C'), 'CITY_TO_CAMPUS', 'Raja Park', '2', current_date, '12:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1500_3_CT2C'), 'CITY_TO_CAMPUS', 'Transport Nagar', '3', current_date, '15:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_1715_2_CT2C'), 'CITY_TO_CAMPUS', 'Transport Nagar', '2', current_date, '17:15:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_2015_3_CT2C'), 'CITY_TO_CAMPUS', 'Ajmeri Gate', '3', current_date, '20:15:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_2100_1_CT2C'), 'CITY_TO_CAMPUS', 'Transport Nagar', '1', current_date, '21:00:00', 35, 5, 'ACTIVE', 'WEEKEND'),
            (CONCAT('TRIP_', DATE_FORMAT(current_date, '%Y%m%d'), '_2100_2_CT2C'), 'CITY_TO_CAMPUS', 'Transport Nagar', '2', current_date, '21:00:00', 35, 5, 'ACTIVE', 'WEEKEND');
        END IF;
        
        SET current_date = DATE_ADD(current_date, INTERVAL 1 DAY);
    END WHILE;
END$$

DELIMITER ;

-- Generate trips for next 30 days
CALL GenerateTripsForDateRange(CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY));

-- Verify generation
SELECT 
    trip_date,
    day_type,
    COUNT(*) as trips_count
FROM trips
WHERE trip_date >= CURDATE()
GROUP BY trip_date, day_type
ORDER BY trip_date;

-- Drop procedure after use (optional)
-- DROP PROCEDURE IF EXISTS GenerateTripsForDateRange;
