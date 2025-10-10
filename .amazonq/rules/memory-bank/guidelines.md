# Development Guidelines

## Code Quality Standards

### Package Structure (5/5 files follow)
- Use reverse domain notation: `com.campusbus.{module}`
- Organize by functional layers: `entity`, `repository`, `lambda`, `config`
- Keep package names lowercase and descriptive

### Class Naming Conventions (5/5 files follow)
- Entity classes: Singular nouns (`Student`, `Trip`)
- Handler classes: Descriptive with "Handler" suffix (`RegisterUserHandler`)
- Configuration classes: Descriptive with "Config" suffix (`DatabaseConfig`)
- Test classes: Class name + "Tests" suffix (`BookingSystemApplicationTests`)

### Field and Method Standards (5/5 files follow)
- Use camelCase for all fields and methods
- Boolean fields without "is" prefix (`penaltyCount`, not `isPenalized`)
- Getter/setter methods follow JavaBean conventions
- Private fields with public accessors

## JPA Entity Patterns

### Entity Annotations (3/3 entities follow)
```java
@Entity
@Table(name = "table_name")
public class EntityName {
    @Id
    private String primaryKey;
    
    @Column(unique = true, nullable = false)
    private String uniqueField;
}
```

### Primary Key Strategy (3/3 entities follow)
- Use String-based IDs with custom generation
- Student IDs: "S" + 8-character UUID (`S12AB34CD`)
- Trip IDs: String-based identifiers
- No auto-increment integers for primary keys

### Default Value Initialization (3/3 entities follow)
```java
private int capacity = 35;
private String status = "ACTIVE";
private LocalDateTime createdAt = LocalDateTime.now();
```

### Column Constraints (2/3 entities follow)
- Use `@Column(nullable = false)` for required fields
- Use `@Column(unique = true)` for unique constraints
- Combine constraints: `@Column(unique = true, nullable = false)`

## Lambda Handler Patterns

### Handler Implementation (1/1 handlers follow)
```java
public class HandlerName implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private static ConfigurableApplicationContext context;
    private static RepositoryType repository;
    
    private void initializeSpringContext() {
        if (context == null) {
            System.setProperty("spring.main.web-application-type", "none");
            context = SpringApplication.run(MainApplication.class);
            repository = context.getBean(RepositoryType.class);
        }
    }
}
```

### Response Format (1/1 handlers follow)
```java
return Map.of(
    "statusCode", 201,
    "body", "Success message"
);

// Error responses
return Map.of("statusCode", 400, "body", "Error message");
return Map.of("statusCode", 500, "body", "Error: " + e.getMessage());
```

### Error Handling (1/1 handlers follow)
- Wrap main logic in try-catch blocks
- Return structured error responses with status codes
- Include exception messages in 500 responses

## Configuration Patterns

### Spring Configuration (1/1 configs follow)
```java
@Configuration
@EnableJpaRepositories(basePackages = "com.campusbus.repository")
@EnableTransactionManagement
public class ConfigClass {
    @Bean
    public DataSource dataSource() {
        // Configuration logic
    }
}
```

### Environment Variable Usage (1/1 configs follow)
```java
String value = System.getenv("ENVIRONMENT_VARIABLE");
```

### Lambda-Optimized Database Config (1/1 configs follow)
```java
config.setMaximumPoolSize(1);           // Critical for Lambda
config.setConnectionTimeout(30000);
config.setIdleTimeout(60000);
config.setLeakDetectionThreshold(60000);
```

## Testing Standards

### Test Class Structure (1/1 test files follow)
```java
@SpringBootTest
class ApplicationNameTests {
    @Test
    void contextLoads() {
        // Basic context loading test
    }
}
```

## Code Formatting

### Indentation and Spacing (5/5 files follow)
- Use 4 spaces for indentation (no tabs)
- Single space after keywords (`if (`, `for (`)
- No space before semicolons
- Consistent brace placement (opening brace on same line)

### Import Organization (5/5 files follow)
- Group imports: Java standard library, third-party, project packages
- No wildcard imports
- Alphabetical ordering within groups

### Comment Standards (5/5 files follow)
- Minimal inline comments (code should be self-documenting)
- Use comments for business logic explanations
- Comment format: `// Description` for single-line comments