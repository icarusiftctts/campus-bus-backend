# Technology Stack

## Programming Languages
- **Java 17**: Primary development language with modern LTS features
- **Maven**: Build automation and dependency management

## Frameworks & Libraries

### Core Framework
- **Spring Boot 3.5.6**: Application framework with auto-configuration
- **Spring Data JPA**: Data access layer with repository abstraction
- **Spring Web**: Web layer components (adapted for serverless)

### Database & Persistence
- **MySQL**: Primary database with MySQL Connector/J
- **Hibernate**: ORM framework with MySQL dialect
- **HikariCP**: High-performance connection pooling

### AWS Integration
- **AWS Lambda Java Core 1.2.3**: Lambda runtime interface
- **AWS Lambda Java Events 3.11.3**: Lambda event handling

### Testing
- **Spring Boot Test**: Comprehensive testing framework
- **JUnit**: Unit testing framework (via Spring Boot Test)

## Build System

### Maven Configuration
```xml
<java.version>17</java.version>
<spring-boot.version>3.5.6</spring-boot.version>
```

### Key Plugins
- **Maven Shade Plugin 3.5.0**: Creates Lambda-deployable uber JAR
- **Spring Boot Maven Plugin**: Spring Boot build integration

## Development Commands

### Build & Package
```bash
./mvnw clean compile          # Compile source code
./mvnw package               # Create JAR with dependencies
./mvnw test                  # Run unit tests
```

### Lambda Deployment
```bash
./mvnw clean package         # Creates Lambda-ready JAR in target/
```

## Configuration Properties

### Database Settings
- JPA auto-DDL with update strategy
- MySQL dialect configuration
- Batch processing optimization (batch_size=25)

### Lambda Optimizations
- Web application type disabled (`spring.main.web-application-type=none`)
- JPA open-in-view disabled for performance
- Optimized logging levels for production

## Development Environment
- **IDE**: Any Java IDE with Maven support
- **Java Runtime**: OpenJDK 17 or compatible
- **Database**: MySQL 8.0+ for development/testing