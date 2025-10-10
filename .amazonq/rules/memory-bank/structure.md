# Project Structure

## Directory Organization

### Core Application Structure
```
src/main/java/com/campusbus/
├── booking_system/          # Main application entry point
├── config/                  # Configuration classes
├── entity/                  # JPA entity models
├── function/               # AWS Lambda function configurations
├── lambda/                 # Lambda handler implementations
└── repository/             # Data access layer
```

### Key Components

#### Application Layer (`booking_system/`)
- **BookingSystemApplication.java**: Spring Boot main class and application entry point

#### Configuration Layer (`config/`)
- **DatabaseConfig.java**: Database connection and JPA configuration for Lambda environment

#### Entity Layer (`entity/`)
- **Student.java**: Student user entity with registration details
- **Trip.java**: Bus trip entity with route, schedule, and capacity information

#### Function Layer (`function/`)
- **RegisterUserFunctionConfig.java**: AWS Lambda function configuration for user registration

#### Lambda Layer (`lambda/`)
- **RegisterUserHandler.java**: Lambda request handler for student registration operations

#### Repository Layer (`repository/`)
- **StudentRepository.java**: JPA repository interface for student data access

## Architectural Patterns

### Serverless Architecture
- AWS Lambda functions for stateless request processing
- Spring Boot framework adapted for serverless deployment
- Maven Shade plugin for Lambda-optimized JAR packaging

### Data Access Pattern
- JPA/Hibernate for object-relational mapping
- Repository pattern for data access abstraction
- MySQL database with connection pooling (HikariCP)

### Configuration Management
- Spring Boot properties for environment-specific settings
- Lambda-optimized configurations (web-application-type=none)
- Database connection optimization for serverless execution

## Build Structure
- **Maven**: Build automation and dependency management
- **Target Directory**: Compiled classes and packaged JAR files
- **Test Structure**: Unit tests following Spring Boot conventions