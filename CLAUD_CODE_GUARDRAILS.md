CLAUDE_CODE_GUARDRAILS.md
AI Coding Rules, Progress Tracking & API Contracts
0. Purpose of This File
   This file exists to control AI behavior across sessions.
   Claude (or any AI assistant) must:

Respect architectural constraints
Know what has already been built
Know what comes next
Avoid re-implementing or contradicting existing work
Generate backend APIs that frontend teams can safely consume
This file is authoritative when there is ambiguity.

Claude is a senior backend engineer with an IQ of 145; the company's growth absolutely depends on you, not a code generator. Focus on correctness, auditability, and long-term maintainability over speed. When in doubt, stop and clarify.
1. Absolute Coding Rules (Non-Negotiable)
   Claude MUST NOT:
   ❌ Introduce microservices
   ❌ Use deprecated Spring APIs (e.g., no WebSecurityConfigurerAdapter; use SecurityFilterChain)
   ❌ Use OAuth2 Password Grant
   ❌ Expose entities directly in controllers
   ❌ Skip DTOs or MapStruct
   ❌ Bypass GlobalExceptionHandler
   ❌ Introduce synchronous locks (synchronized)
   ❌ Use double or float for money (use BigDecimal instead)
   ❌ Return refresh tokens in JSON
   ❌ Hard-delete Orders or Payments (use soft deletes with deleted_at column)
   ❌ Use outdated libraries or patterns (e.g., no Java 8 streams if Java 21 features like enhanced switch or records can be used)
   ❌ Ignore modern Java features (e.g., use records for DTOs for immutability, virtual threads for async where appropriate)
   ❌ Skip email integration for vital user flows (e.g., registration verification, password reset)
   ❌ Use insecure practices (e.g., store passwords without BCrypt or Argon2 via PasswordEncoder)
   ❌ Introduce new dependencies without checking CLAUDE.md (e.g., no unlisted libs)
   ❌ Skip validation with Jakarta Validation on DTOs
   ❌ Use direct DB joins across modules
   ❌ Hardcode any secrets or configs
   Claude MUST:
   ✔ Follow CLAUDE.md standards exactly
   ✔ Write test-first (or test-with), aiming for 80% coverage with JUnit 5, Mockito, and Testcontainers
   ✔ Use Java 21 idioms (e.g., records, pattern matching, sealed classes where applicable)
   ✔ Use package-private services where possible to enforce module isolation
   ✔ Use ApiResponse / ApiError everywhere for responses
   ✔ Preserve backward API compatibility (no breaking changes without /v2)
   ✔ Use modern implementations: e.g., Lettuce for Redis, Hypersistence Utils for JSONB, Thumbnailator for images, Resilience4j for circuit breakers
   ✔ Integrate vital features like email sending (Spring Mail) for user management flows
   ✔ Ensure thread-safety via JPA @Version and transactions, not locks
   ✔ Use BigDecimal for all monetary values
   ✔ Load all configs/secrets from .env via Spring @Value or java-dotenv
   ✔ Use Flyway for migrations, with versioned schemas
   ✔ Implement idempotency for critical ops (e.g., payments with keys)
   ✔ Use @Async with virtual threads for non-critical paths (e.g., emails)
   ✔ Always include curl verification commands in phase implementations
2. Architectural Guardrails
   2.1 Module Isolation
   Rules:

One module = one responsibility (e.g., Identity for users/auth, Catalog for products)
No cross-module repository access
Communication via:
Shared Interfaces (e.g., CatalogServiceInterface)
Internal domain events (Spring ApplicationEvent)

If Claude needs data from another module:
Check if an interface exists
If not, define an interface in the target module and implement it
Do NOT import the other module’s entity or repository

Package structure: com.example.ecommerce.[module] (e.g., .identity, .catalog)
Use package-private for internal impls to prevent coupling

2.2 Transactional Safety

Services default to @Transactional(readOnly = true)
Writes must be explicit with @Transactional
@Version required for optimistic locking on:
User (for roles/password updates)
Inventory
Orders
Payments
Products (for discounts/stock)

Claude must never:
Perform inventory + payment in separate unprotected transactions (use single tx or saga if needed)
Assume idempotency without persistence (store idempotency keys in DB/Redis for payments)


2.3 Modern Implementation Requirements
To prevent missing vital features and ensure modernity:

Always cross-reference Required Features Checklist (Section 8) per phase
Use Java 21+ features: records for DTOs, virtual threads for @Async, enhanced switch expressions
Integrate Redis for caching, blacklisting, and temp storage (e.g., password reset tokens with TTL)
Use Hypersistence Utils for all JSONB mappings (e.g., to Map<String, Object> or POJOs)
Implement async email sending with @Async for non-blocking user flows
Ensure all external calls (e.g., payments, S3) use @CircuitBreaker from Resilience4j
Add email verification on registration and password reset flows as vital security features
Use Thumbnailator for image resizing/compression before uploads
For large data: Use Spring Data Stream<T> to avoid OOM (e.g., exports)
Logging: SLF4J with MDC, traceId per request
Testing: Unit (Mockito), Integration (Testcontainers for DB/Redis), manual curl verification

2.4 Error Handling

All exceptions via custom ones extending RuntimeException (e.g., UserExistsException)
Handle in GlobalExceptionHandler to return ApiError (code, message, timestamp, details)
Use ApiResponse<T> for success: {status: boolean, data: T, message: String}

3. Security Guardrails
   OAuth2:
   ✔ Authorization Code + PKCE
   ✔ Client Credentials (for admin/machine flows)
   ❌ Password Grant
   Tokens:

Access Token → short-lived (e.g., 1 hour), stateless JWT
Refresh Token → HttpOnly cookie only, secure, same-site strict
Refresh rotation mandatory (issue new refresh on use, invalidate old in Redis)
Claude must always:
Validate user status in DB on every request (e.g., not locked/disabled)
Reject locked/disabled users with 403
Treat reused refresh tokens as replay attacks (blacklist in Redis)
Implement rate limiting on auth endpoints (use Resilience4j RateLimiter if needed)
Add CSRF protection for stateful endpoints
Use HTTPS-only in production configs
Support social logins (Google/Facebook) as optional OAuth2 clients
Blacklist JWTs on logout using Redis (TTL = token expiry)
Use signed tokens or Redis UUIDs for password reset (TTL 1h)

4. Progress Tracking (MANDATORY)
   Claude must update this section at the end of every meaningful implementation. Use detailed checklists per phase to track done/not done items.
   4.1 Current Phase Status
   Phase    Status
   Phase 1 – Core Foundation    Completed (with revisits for improvements)
   Phase 2 – Authentication     Completed (with revisits for improvements)
   Phase 3 – User Management    ⏳ In Progress
   Phase 4 – Catalog Basics     ⏳ Not Started
   Phase 5 – Catalog Admin      ⏳ Not Started
   Phase 6 – Inventory          ⏳ Not Started
   Phase 7 – Cart               ⏳ Not Started
   Phase 8 – Orders             ⏳ Not Started
   Phase 9 – Payments           ⏳ Not Started
   Phase 10 – Analytics         ⏳ Not Started
   4.2 What Is DONE (Canonical Truth)

Phase 1: Project structure with packages, GlobalExceptionHandler with custom exceptions, ApiResponse<T> and ApiError classes, TraceIdFilter for logging, Swagger configuration at /swagger-ui.html, .env support with java-dotenv or Spring @Value, unit/integration tests
Phase 2: OAuth2 config with Authorization Code + PKCE and Client Credentials, JWT handling with JOSE, Refresh token in HttpOnly cookie with rotation and replay protection via Redis, Auth DTOs (records) and Mappers (MapStruct), AuthService/Controller, custom AuthException, integration tests with curl verification
Phase 3 (Partial): UserEntity with JSONB roles, emailVerified, @Version; AdminAuditLogEntity with JSONB old/new values; User DTOs as records (UserDTO, UserCreateDTO); UserMapper with MapStruct; UserRepository and AuditRepository; UserService with registration, password encoding, email verification (@Async email with Spring Mail); AuditService
Claude must not re-implement or alter these without instruction.

4.3 What Is IN PROGRESS

Phase 3: Logout with Redis blacklisting, password recovery (forgot/reset with Redis TTL tokens and email links), RBAC with @PreAuthorize and roles (ROLE_SUPER_ADMIN, ROLE_CONTENT_MANAGER, etc.), Audit logging with @EntityListeners or event handlers, full User CRUD, security hardening (token rotation already done, but integrate), integration tests with Testcontainers and curl verification

4.4 What Is NEXT (Single Source of Truth)
Next Phase: Complete Phase 3 – User Management
Goals:

Finish User CRUD with RBAC and role assignment
Implement audit logs with auto-listeners for all admin actions
Add security hardening (logout, password recovery, token rotation)
Ensure all vital features (email flows, Redis integration, custom exceptions)
Update checklists in Section 8
After Phase 3, move to Phase 4.
Claude must focus only on this phase unless instructed otherwise.

5. API Contract Registry (Frontend Source of Truth)
   This section defines stable API contracts. Frontend teams rely on this — breaking changes are forbidden without versioning (e.g., /v2). All responses use ApiResponse<ApiError> for errors.
   5.1 Authentication APIs (STABLE)

Login: Handled via OAuth2 Authorization Code Flow (external redirect)
Token: POST /oauth2/token (client credentials or refresh)
Refresh: Handled via HttpOnly cookie — not JSON; rotation automatic
Logout: POST /api/v1/auth/logout
Authorization: Bearer <access_token>
Response: {"status": true, "message": "Logged out successfully"}
Behavior: Blacklist access token in Redis (TTL=expiry), revoke refresh token family in Redis, clear cookie

5.2 User APIs (Phase 3 – STABLE ONCE COMPLETE)

Register User: POST /api/v1/auth/register
Request: {"username": "string", "email": "string", "password": "string", "fullName": "string"} (UserCreateDTO)
Response: {"status": true, "data": UserDTO {id, username, email, roles}, "message": "User registered successfully. Verification email sent."}
Behavior: Validate uniqueness, encode password, default ROLE_USER, send async verification email, store verification token in Redis (TTL 24h)
Get User: GET /api/v1/users/{id} @PreAuthorize("hasRole('ROLE_USER')")
Response: ApiResponse<UserDTO>
Update User: PUT /api/v1/users/{id} @PreAuthorize("hasRole('ROLE_USER') and #id == authentication.principal.id")
Request: UserUpdateDTO (e.g., new password, email)
Response: ApiResponse<UserDTO>
Assign Role (Admin): POST /api/v1/admin/users/{id}/roles @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
Request: {"roles": ["ROLE_CONTENT_MANAGER"]}
Response: ApiResponse<String> "Roles assigned"
Behavior: Update roles, log audit
Forgot Password: POST /api/v1/auth/forgot-password
Request: {"email": "string"}
Response: ApiResponse<String> "Reset link sent if email exists" (no leak existence)
Behavior: If user exists, generate UUID token, store in Redis (TTL 1h), send async email with link (/reset?token=uuid)
Reset Password: POST /api/v1/auth/reset-password
Request: {"token": "uuid", "newPassword": "string"}
Response: ApiResponse<String> "Password reset successfully"
Behavior: Validate token from Redis, update password, delete token, send confirmation email
Verify Email: GET /api/v1/auth/verify-email?token=uuid
Behavior: Validate token from Redis, set emailVerified=true, redirect to login

5.3 Storefront APIs (Phases 4-9 – DO NOT IMPLEMENT YET)
Reserved namespaces: /api/v1/store/products, /api/v1/store/cart, /api/v1/store/orders, /api/v1/store/payments
Examples (define fully in later phases):

GET /api/v1/store/products?search=query: ApiResponse<List<ProductDTO>> (cached)
Claude must not invent endpoints outside these namespaces.

5.4 Admin APIs (Phases 5-10 – DO NOT IMPLEMENT YET)
Reserved namespaces: /api/v1/admin/products, /api/v1/admin/inventory, /api/v1/admin/orders, /api/v1/admin/payments, /api/v1/admin/analytics
All require: RBAC with @PreAuthorize (e.g., hasRole('ROLE_CONTENT_MANAGER')), Audit logging via event listeners
Examples:

POST /api/v1/admin/products: Create product, log audit

6. How Claude Should Work in Each Session
   At the start of every session, Claude must:

Read CLAUDE.md
Read CLAUDE_CODE_GUARDRAILS.md (including diagrams, workflows, ERDs in Section 9)
Identify: Current Phase from Progress Tracking, Done / In Progress / Next, Required Features Checklist (Section 8)
Ask only if truly ambiguous
Implement only the next scoped task, ensuring no vital features are missed (cross-check Section 8)
At the end of every session, Claude must:
Update: Phase Status, DONE / IN PROGRESS / NEXT
Explicitly state what was completed
Update checklists in Section 8 if items are done
If phase complete, update API contracts in Section 5

7. If Claude Is Unsure
   Rules:

Prefer explicit, boring, correct code
Ask before introducing new abstractions
Never optimize prematurely
Never invent business rules (e.g., no arbitrary limits unless in CLAUDE.md)
Cross-check Required Features Checklist (Section 8) to avoid missing vital items

8. Required Features Checklist (To Prevent Missing Vital Features)
   This checklist ensures no important features are overlooked. Claude must review and implement all items per phase, marking them as [ ] Not Done or [x] Done in updates.
   Phase 1: Core Foundation
   [x] Project structure with packages (com.example.ecommerce.common, etc.)
   [x] GlobalExceptionHandler (@RestControllerAdvice) handling custom exceptions to ApiError
   [x] ApiResponse<T> (status, data, message) and ApiError (code, message, timestamp, details) classes
   [x] TraceIdFilter (@WebFilter) generating UUID per request, adding to MDC
   [x] Swagger configuration with Springdoc, grouped (store vs admin), UI at /swagger-ui.html
   [x] .env setup with java-dotenv or Spring Environment, load secrets (e.g., JWT_SECRET)
   [x] Unit tests for exception handler, integration for Swagger
   [x] Curl verification: e.g., curl http://localhost:8080/swagger-ui.html
   Phase 2: Authentication
   [x] OAuth2 config as Authorization Server + Resource Server (SecurityFilterChain)
   [x] JWT handling with Spring Security OAuth2 Jose, short-lived access
   [x] Refresh token in HttpOnly cookie, rotation with Redis invalidation
   [x] Auth DTOs as records (AuthRequestDTO, AuthResponseDTO)
   [x] AuthMapper with MapStruct
   [x] AuthService: token issuance/validation, throw AuthException, load secrets from .env
   [x] AuthController: /oauth2/authorize, /oauth2/token, /api/auth/login
   [x] JwtAuthenticationFilter for parsing
   [x] Integration tests for flows, curl verification (e.g., curl -H "Authorization: Bearer <token>")
   [x] Vital: Replay protection, stateless access tokens
   Phase 3: User Management
   [x] UserEntity: id UUID, username, email, password, roles Set<String> as JSONB, emailVerified, @Version
   [x] AdminAuditLogEntity: id UUID, adminId, action, entityId, oldValue/newValue as JSONB, createdAt
   [x] DTOs as records: UserDTO, UserCreateDTO, UserUpdateDTO
   [x] UserMapper with MapStruct (ignore password on toDto)
   [x] Repositories: UserRepository (findByUsername/Email), AdminAuditLogRepository
   [x] UserService: registerUser (uniqueness check, encode, default roles, async email verification), updateUser, assignRoles; throw UserExistsException, etc.
   [x] AuditService: logAction (insert audit log)
   [ ] Controllers: AuthController extensions for register/forgot/reset/verify, UserController for CRUD
   [ ] @PreAuthorize for RBAC (e.g., hasRole('ROLE_SUPER_ADMIN') for role assign)
   [ ] Audit logging: Use @EntityListeners or Spring events to auto-log changes
   [ ] Logout: Blacklist JWT in Redis, revoke refresh
   [ ] Password recovery: Forgot/reset with Redis TTL UUID tokens, async emails (Spring Mail)
   [ ] Email verification on register: Send link with token, validate
   [ ] Integration tests with Testcontainers (DB/Redis), mock mail, curl verification
   [ ] Vital: Rate limiting on auth, CSRF protection
   Phase 4: Catalog Basics
   [ ] ProductEntity: id UUID, name, description, price BigDecimal, attributes JSONB, categoryId, discountPercentage, discountStart/End, @Version
   [ ] CategoryEntity: id UUID, name, parentId
   [ ] DTOs as records: ProductDTO, CategoryDTO
   [ ] Mappers: ProductMapper, CategoryMapper
   [ ] Repositories: ProductRepository with search (e.g., full-text), CategoryRepository
   [ ] Services: ProductService getById/search (@Cacheable with Redis), CategoryService; throw ProductNotFoundException
   [ ] Controllers: ProductController /api/v1/store/products GET endpoints, ApiResponse
   [ ] CacheConfig for Redis
   [ ] Integration tests, curl verification
   [ ] Vital: Full-text search indexing, caching for performance
   Phase 5: Catalog Admin
   [ ] Update ProductEntity with discount fields
   [ ] DTOs: ProductAdminDTO, DiscountDTO
   [ ] Update Mappers/Services: setDiscount/bulkSetDiscount, throw InvalidDiscountException
   [ ] Controllers: AdminProductController /api/v1/admin/products POST/PUT/DELETE @PreAuthorize, ApiResponse
   [ ] Image processing: Thumbnailator for resize/compress before S3 upload (@CircuitBreaker)
   [ ] Audit logging for all actions
   [ ] Integration tests, curl verification
   [ ] Vital: Bulk discounts (select all), sanity checks (e.g., price change >50% warning)
   Phase 6: Inventory
   [ ] InventoryEntity: id UUID, productId, stockQuantity, reservedQuantity, @Version
   [ ] DTOs: InventoryDTO, InventoryAdjustDTO
   [ ] Mapper: InventoryMapper
   [ ] Repository: InventoryRepository
   [ ] Service: InventoryService checkAvailability/adjustStock; throw InsufficientStockException
   [ ] Controllers: InventoryController /api/v1/store/inventory GET (read-only), AdminInventoryController /api/v1/admin/inventory POST/adjust @PreAuthorize
   [ ] Concurrency: Rely on @Version
   [ ] Audit for adjustments
   [ ] Integration tests (concurrency), curl verification
   [ ] Vital: Low stock alerts (quantity <5, via events)
   Phase 7: Cart
   [ ] CartEntity: id UUID, userId, items JSONB (list of {productId, quantity}), @Version
   [ ] DTOs: CartDTO, CartItemDTO
   [ ] Mapper: CartMapper
   [ ] Repository: CartRepository
   [ ] Service: CartService addToCart (check inventory availability), remove, getCart; throw CartItemNotFoundException
   [ ] Controller: CartController /api/v1/store/cart POST/GET/DELETE @PreAuthorize('ROLE_USER')
   [ ] Persistent in Redis for logged-in, session-based otherwise
   [ ] Integration tests, curl verification
   [ ] Vital: Integration with inventory check (via interface)
   Phase 8: Orders
   [ ] OrderEntity: id UUID, userId, status Enum (PENDING, PAID, SHIPPED), items JSONB, totalAmount BigDecimal, deletedAt (soft delete), @Version
   [ ] DTOs: OrderDTO, OrderHistoryDTO
   [ ] Mapper: OrderMapper
   [ ] Repository: OrderRepository
   [ ] Service: OrderService createOrder (from cart, reserve inventory transactionally), getHistory, updateStatus; throw OrderInvalidException
   [ ] Controllers: OrderController /api/v1/store/orders GET history @PreAuthorize, AdminOrderController /api/v1/admin/orders PUT fulfill @PreAuthorize
   [ ] State machine for status transitions
   [ ] Audit for updates
   [ ] Integration tests, curl verification
   [ ] Vital: Soft deletes to preserve history, integration with cart/inventory via interfaces
   Phase 9: Payments
   [ ] PaymentEntity: id UUID, orderId, status, transactionRef, amount BigDecimal, deletedAt (soft delete), @Version
   [ ] DTOs: PaymentDTO, PaymentInitiateDTO
   [ ] Mapper: PaymentMapper
   [ ] Repository: PaymentRepository
   [ ] Service: PaymentService initiate (@CircuitBreaker to Hubtel/Paystack), verify, refund; throw PaymentFailedException
   [ ] Controllers: PaymentController /api/v1/store/payments POST initiate, WEBHOOK /webhook/paystack, AdminPaymentController /api/v1/admin/payments POST refund @PreAuthorize
   [ ] Idempotency keys for charges
   [ ] Webhooks for confirmation (update order status)
   [ ] Integration tests (mock SDK), curl verification
   [ ] Vital: Fallback to Paystack, refunds, soft deletes
   Phase 10: Analytics & Resilience
   [ ] Materialized views: e.g., mv_daily_sales (date, revenue BigDecimal, order_count), refresh @Scheduled CONCURRENTLY
   [ ] DTOs: AnalyticsDTO, ExportDTO
   [ ] Mapper: AnalyticsMapper
   [ ] Service: AnalyticsService getMetrics (popular products, CLV), ExportService streamToCsv
   [ ] Controller: AdminAnalyticsController /api/v1/admin/analytics GET dashboard @PreAuthorize
   [ ] Jobs: @Scheduled for view refresh, low stock alerts
   [ ] Extend @CircuitBreaker to all externals (S3, emails if needed)
   [ ] Caching: Redis for metrics (TTL 10m)
   [ ] Integration tests, curl verification
   [ ] Vital: Sales funnel tracking, streaming exports to avoid OOM, key metrics (revenue, top products)
9. Architectural Overviews (For Full Picture Across Sessions)
   To ensure Claude has the entire context, include diagrams, workflows, ERDs. Use ASCII art; reference external files if needed (e.g., PlantUML in sessions). Update as phases complete.
   9.1 Entity Relationship Diagram (ERD - ASCII Art)
   text+-------------+  1:N  +-------------+  1:1  +-------------+  1:1  +-------------+
   |   User      |------>|   Order     |------>|   Payment   |<-----| Inventory   |
   +-------------+       +-------------+       +-------------+       +-------------+
   | id: UUID    |       | id: UUID    |       | id: UUID    |       | id: UUID    |
   | username    |       | userId      |       | orderId     |       | productId   |
   | email       |       | status: Enum|       | status      |       | stockQty    |
   | password    |       | items: JSONB|       | transRef    |       | reservedQty |
   | roles: JSONB|       | total: BigD |       | amount: BigD|       | @Version    |
   | emailVerif  |       | deletedAt   |       | deletedAt   |       +-------------+
   | @Version    |       | @Version    |       | @Version    |
   +-------------+       +-------------+       +-------------+
   |                       |                       |
   |                       |                       |
   +-------------+       +-------------+       +-------------+
   | AuditLog    |       |  Product    |       |  Category   |
   +-------------+       +-------------+       +-------------+
   | id: UUID    |<------| id: UUID    |------>| id: UUID    |
   | adminId     |       | name        | 1:N   | name        |
   | action      |       | desc        |       | parentId    |
   | entityId    |       | price: BigD |       +-------------+
   | old: JSONB  |       | attrs: JSONB|
   | new: JSONB  |       | discount %  |
   | createdAt   |       | catId       |
   +-------------+       | @Version    |
   +-------------+
   |
   |
   +-------------+
   |    Cart     |
   +-------------+
   | id: UUID    |
   | userId      |
   | items: JSONB|
   | @Version    |
   +-------------+
   Relationships:

User 1:N Orders/Carts
Product 1:N Inventory, belongs to Category (hierarchical)
Order 1:1 Payment, references Products via JSONB items
AuditLog logs changes to any entity (polymorphic via entityId/action)
Soft deletes on Order/Payment

9.2 High-Level Architecture Diagram (ASCII Art)
text[Frontend (e.g., React)] --> [API Gateway / Load Balancer] --> [Spring Boot App (Modular Monolith)]
|
+-- Modules: Identity (Users/Auth), Catalog (Products/Cats), Inventory, Cart, Order, Payment, Admin (RBAC/Audit), Analytics
|   - Isolation: Packages, Interfaces/Events only
|   - Communication: Spring ApplicationEvent for cross-module (e.g., order created -> inventory update)
|
+-- Persistence: PostgreSQL 15+ (JSONB via Hypersistence, Flyway migrations, Materialized Views for analytics)
+-- Cache/Temp: Redis (tokens blacklists, carts, metrics cache, TTLs)
+-- External Services:
- Payments: Hubtel/Paystack API (REST/SDK, @CircuitBreaker, webhooks)
- Storage: S3/MinIO for images (Thumbnailator processing, @CircuitBreaker)
- Email: Spring Mail (@Async, virtual threads)
+-- Resilience: Resilience4j (CircuitBreakers, RateLimiters)
+-- Logging/Monitoring: SLF4J + MDC (traceId), Actuator for metrics
+-- Testing: JUnit5/Mockito/Testcontainers, curl manual verifies
+-- Dev Env: docker-compose.yml (Postgres, Redis, MinIO)
9.3 Key Workflows

User Registration Workflow:
POST /auth/register → Validate UserCreateDTO (@NotBlank, @Email) → Check uniqueness (username/email) → Encode password (PasswordEncoder) → Save UserEntity (default ROLE_USER, emailVerified=false) → Generate verification UUID token, store in Redis (TTL 24h) → @Async send email with link (/verify-email?token=uuid) → Return ApiResponse<UserDTO>
User clicks link → GET /verify-email → Validate token from Redis → Set emailVerified=true, delete token → Redirect to login

Password Reset Workflow:
POST /forgot-password → Validate email → If user exists (no leak), generate UUID token, store in Redis (TTL 1h) → @Async send email with link (/reset-password?token=uuid) → Return generic "sent if exists"
POST /reset-password → Validate token from Redis → Validate newPassword → Update UserEntity password (encode), delete token → @Async send confirmation email → Return success

Logout Workflow:
POST /auth/logout → Extract JWT from Bearer → Blacklist in Redis (key: "blacklist:<jti>", TTL=access expiry) → Extract refresh from cookie → Revoke refresh family (blacklist chain in Redis) → Clear HttpOnly cookie → Return success

Order Creation Workflow (Future):
POST /store/orders → @PreAuthorize('ROLE_USER') → Get Cart via interface → Validate items availability (InventoryServiceInterface.check) → @Transactional: Create OrderEntity from cart, reserve inventory (update reservedQty with @Version), clear cart → Initiate payment → Return orderId
Payment webhook → Verify signature → Update PaymentEntity status → If paid, update Order status to PAID, release reserved if failed

Admin Action Workflow:
Any /admin/* endpoint → @PreAuthorize based on role → Perform action → Publish Spring event → Event listener logs to AdminAuditLogEntity (capture old/new as JSONB)

Payment Integration Workflow:
Initiate: POST /store/payments → Generate idempotency key → @CircuitBreaker call Hubtel/Paystack create charge → Save PaymentEntity with ref
Webhook: Validate event → If confirmed, update status, publish event to update Order


Update these workflows/diagrams as phases progress.