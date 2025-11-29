# Warehouse Management Backend - AI Coding Agent Instructions

## Project Overview
Spring Boot 3.4.5 warehouse management system (QuanLyKhoBE) with JWT + OAuth2 authentication, role-based permission system, and inventory tracking. Java 17, MySQL database, Vietnamese business domain terminology.

## Architecture

### Authentication & Security Flow
- **Dual Auth**: JWT tokens for API access + Google OAuth2 for SSO login
- **JWT Filter Chain**: `JwtAuthenticationFilter` → `PermissionInterceptor` → Controller
  - JWT validates identity, interceptor enforces role-permissions mapping
  - Auth bypass: `/api/auth/**`, `/swagger-ui/**`, public endpoints in `SecurityConfig`
- **Permission System**: Many-to-many Role ↔ Permission via `role_permissions` junction table
  - Permissions stored as: `{name, apiPath, method, module}` e.g., `GET /api/admin/users/{id}`
  - Path normalization in `PermissionInterceptor`: converts `/123` → `/{id}`, `/ABC123` → `/{productCode}`
  - Check order: exact path → `{id}` pattern → `:id` pattern → `[0-9]+` pattern

### Data Model Key Relationships
```
User →(ManyToOne)→ Role →(ManyToMany)→ Permission
Product →(ManyToOne)→ Supplier
Inventory: Product + Location + quantity (current stock)
InventorySnapshot: Product + quantity + snapshotDate (monthly archive)
StockInForm → StockInDetail[] (invoice + line items)
StockOutForm → StockOutDetail[] (shipment + line items)
```

### Critical Services
- **StockInService/StockOutService**: Use `@Transactional` to ensure atomicity when updating inventory + creating form records
- **SnapshotJob**: `@Scheduled(cron = "0 0 0 1 * ?")` monthly inventory archiving at midnight
- **StockCheckScheduler**: `@Scheduled(cron = "0 59 23 L * ?")` end-of-month stock verification
- **PermissionService**: Native SQL for `role_permissions` operations (INSERT ON DUPLICATE KEY, manual DELETE queries)

## Development Patterns

### Entity Conventions
- All entities use Lombok `@Data`, `@Getter`, `@Setter`
- Avoid `@ToString` on relationships → Use `@ToString.Exclude` + manual toString() for lazy-loaded associations
- `User.roleName` denormalized field synced via `@PrePersist/@PreUpdate` lifecycle hooks
- Timestamps: `createdAt` (immutable), `updatedAt`, `deletedAt` (soft delete marker)

### Transaction Management
- Mark read operations with `@Transactional(readOnly = true)` for optimization
- Use `@Transactional` on service methods that modify multiple entities (stock operations, role-permission assignments)
- Repository methods inherit transaction context from service layer

### DTOs & Validation
- **Request DTOs**: `src/main/java/.../dto/request/` - use `@Valid` in controllers
- **Response DTOs**: `src/main/java/.../dto/response/` - flatten complex entity graphs for API responses
- `ResultPaginationDTO`: Standard pagination wrapper for all paginated endpoints

### Filtering & Search
- Use JPA `Specification` for dynamic query building (see `PermissionSpecification`)
- Repositories extend `JpaSpecificationExecutor<T>` for spec-based queries
- Custom native queries for complex joins or bulk operations

### File Handling
- **QR Codes**: Generated in `uploads/qrcode/` via Google ZXing library
- **Invoices**: Uploaded to `uploads/hoadon/` via `MultipartFile`
- **Excel Export**: Apache POI for report generation (see `ExcelExportService`)

## Environment Setup

### Required Configuration (.env file)
```properties
# Database - MySQL 8.0
SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=true
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect

# JWT - Must be base64 encoded
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000

# OAuth2 Google
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google

# Mail (for password reset)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
```

### Build & Run
```powershell
# Build with Maven wrapper
.\mvnw clean install

# Run application (port 8080 by default)
.\mvnw spring-boot:run

# Run tests
.\mvnw test
```

### Database Initialization
- DDL auto-update enabled - tables created on first run
- Initial data seeding: Manually insert root admin user + default roles/permissions
- Schema: `warehouse` database, ensure MySQL service running on localhost:3306

## Common Tasks

### Adding New Permission-Protected Endpoint
1. Define endpoint in controller with `@PreAuthorize` or no annotation (defaults to authenticated)
2. Insert permission record in `permissions` table: `(name, apiPath, method, module)`
3. Link to role via `role_permissions` insert
4. Interceptor auto-validates on request

### Modifying Role Permissions
Use `PermissionService.assignPermissionsToRole(roleId, permissionIds)` or `revokePermissionsFromRole()`
- Native SQL with `ON DUPLICATE KEY UPDATE` for idempotent assignment

### Creating Stock Operations
- POST to `/api/stock-in` or `/api/stock-out` with `@Transactional` service method
- Update `Inventory.quantity` atomically
- Generate form record (`StockInForm`/`StockOutForm`) + detail lines

### Excel Reporting
- Inject `ExcelExportService` → call `generateReport(data)`
- Returns `ResponseEntity<Resource>` with Excel MIME type
- Used in `ReportController` for analytics exports

## Testing Notes
- Spring Security test support: Use `@WithMockUser` for controller tests
- Repository tests: `@DataJpaTest` with H2 in-memory database
- Integration tests: Full Spring Boot context with test containers for MySQL

## Naming Conventions
- **Variables**: camelCase (Vietnamese terms accepted in domain: `duongDanApi`, `phuongThuc`)
- **Endpoints**: kebab-case (`/api/stock-in`, `/api/admin/users`)
- **Entity fields**: snake_case in database, camelCase in Java (JPA auto-converts)

## Known Issues & Workarounds
- **Lazy loading outside transaction**: Use `@Transactional(readOnly=true)` on service methods or DTO projections
- **Circular dependencies**: Use `@Lazy` annotation on constructor parameters (e.g., `SecurityConfig` dependencies)
- **Path variable patterns**: PermissionInterceptor tries multiple normalizations - add new patterns to `possiblePaths` list if needed
- **CORS**: Hardcoded for localhost:3000 - update `SecurityConfig.corsConfigurationSource()` for production

## API Documentation
- Swagger UI: http://localhost:8080/swagger-ui.html (auto-generated via SpringDoc OpenAPI)
- All endpoints documented with `@Operation`, `@ApiResponse` annotations
