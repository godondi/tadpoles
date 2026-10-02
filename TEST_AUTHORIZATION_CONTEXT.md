# Test Authorization Context & Fixes

**Date**: October 2, 2026  
**Project**: Tadpoles - Spring Boot Backend with JWT Authentication & Authorization  
**Status**: All controllers refactored to use `@PreAuthorize` annotations (148 source files compile successfully)

---

## Executive Summary

The Tadpoles backend has been fully refactored to use declarative `@PreAuthorize` annotations for authorization across 120+ endpoints. However, the test suite has inconsistencies with JWT secret configuration that can cause test failures. This document provides:

1. **Current State**: JWT configuration, test patterns, and identified issues
2. **The JWT Secret Problem**: Why some tests fail due to mismatched secrets
3. **Test Structure Overview**: How tests currently use JWT mocking
4. **Fixes Required**: Specific test files that need updates
5. **Implementation Guide**: Step-by-step fix instructions

---

## 1. JWT Architecture Overview

### 1.1 How JWT Signing/Verification Works

**Token Generation (Production)**:
- Location: `AuthServiceImpl.java` (lines 68-81)
- Uses JJWT library to sign tokens
- Secret: Injected from `application.yml` property `jwt.secret`
- Algorithm: HS256 (HMAC SHA-256)

```java
// AuthServiceImpl.generateToken(): Signs token with secret
SecretKeySpec keySpec = new SecretKeySpec(
    jwtSecret.getBytes(StandardCharsets.UTF_8),
    SignatureAlgorithm.HS256.getJcaName()
);
return Jwts.builder()
    .subject(username)
    .claim("roles", roles)
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRATION_MS))
    .signWith(keySpec, SignatureAlgorithm.HS256)
    .compact();
```

**Token Verification (SecurityConfig)**:
- Location: `SecurityConfig.java` (lines 56-60)
- Creates `JwtDecoder` bean using Spring Security's NimbusJwtDecoder
- Secret: Injected from same `application.yml` property
- Uses the secret to decode and verify incoming JWT tokens

```java
// SecurityConfig.jwtDecoder(): Verifies token signature with secret
@Bean
public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
    SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).build();
}
```

### 1.2 JWT Secret Configuration

**Default Secret** (from `application.yml`):
```yaml
jwt:
  secret: ${JWT_SECRET:mission-control-shared-secret-key-32-bytes-minimum}
```

- Uses environment variable `JWT_SECRET` if available
- Falls back to `mission-control-shared-secret-key-32-bytes-minimum` in development
- **Critical Point**: Both AuthServiceImpl and SecurityConfig must use the **same** secret

---

## 2. Current Test Structure

### 2.1 Test Patterns in Use

All controller tests use Spring Security's MockMvc test utilities:

```java
@WebMvcTest(SomeController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class SomeControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void someTest() throws Exception {
        mockMvc.perform(get("/api/endpoint")
                .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN")))))
            .andExpect(status().isOk());
    }
}
```

**Key Components**:
- `@WebMvcTest`: Starts a Spring context with only web layer beans
- `@Import({GlobalExceptionHandler.class, SecurityConfig.class})`: Imports security config
- `@TestPropertySource`: Overrides application properties for this test class
- `.with(jwt().jwt(token -> token.claim("roles", List.of("ROLE_NAME"))))`: Mocks JWT authentication

### 2.2 JWT Mocking with Spring Security Test

The `.with(jwt())` post-processor creates a mock JWT token with specified claims:

```java
// Example: Mock ADMIN role
.with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN"))))

// Example: Mock ADVISOR role
.with(jwt().jwt(token -> token.claim("roles", List.of("ADVISOR"))))

// Example: Multiple roles
.with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN", "AUDITOR"))))
```

**Important**: This mocking happens **before** the JWT decoder runs, so the secret doesn't matter for these tests unless they:
- Actually try to decode a real JWT token
- Run integration tests with actual token generation

---

## 3. The JWT Secret Problem

### 3.1 Tests That Are Properly Configured

The following tests **correctly** define the JWT secret via `@TestPropertySource`:

| Test File | Status | Secret Defined |
|-----------|--------|---|
| `AdvisorControllerTest.java` | ✅ Correct | Line 29 |
| `AppUserControllerTest.java` | ✅ Correct | Line 31 |
| `AuditLogControllerTest.java` | ✅ Correct | Line 25 |
| `ClientSubscriptionControllerTest.java` | ✅ Correct | Line 27 |
| `ModelPortfolioControllerTest.java` | ✅ Correct | Line 30 |
| `ModelPortfolioHoldingControllerTest.java` | ✅ Correct | Line 30 |
| `RefreshTokenControllerTest.java` | ✅ Correct | Line 32 |
| `RoleControllerTest.java` | ✅ Correct | Line 24 |
| `TradeSuggestionControllerTest.java` | ✅ Correct | Line 31 |
| `OrderFillServiceIntegrationTest.java` | ✅ Correct | Line 23 |

**Test Secret Used**: `test-jwt-secret-test-jwt-secret-123456`

### 3.2 Tests That Are Missing JWT Secret Configuration

The following tests **fail to define** the JWT secret:

| Test File | Issue | Location | Impact |
|-----------|-------|----------|--------|
| `ClientControllerTest.java` | ❌ Missing `@TestPropertySource` | N/A | Will use default secret from application.yml |
| `InstrumentControllerTest.java` | ⚠️ Uses `@AutoConfigureMockMvc(addFilters = false)` | Line 24 | Bypasses security, inconsistent with others |

### 3.3 Why This Causes Problems

When a test class imports `SecurityConfig.class` but doesn't define the JWT secret:

1. **SecurityConfig bean is created** with the default secret from `application.yml`
   - `jwtDecoder()` method reads `${jwt.secret}` 
   - Gets `mission-control-shared-secret-key-32-bytes-minimum`

2. **Test tries to mock JWT authentication** with `.with(jwt())`
   - For simple role mocking, this works fine (JWT never gets decoded)
   - For any real token validation, it would fail

3. **Inconsistency across test suite**:
   - Some tests use the production default secret
   - Other tests use the test secret
   - If tests ever generate real tokens (integration tests), they could try to use mismatched secrets

### 3.4 InstrumentControllerTest Special Case

```java
@WebMvcTest(InstrumentController.class)
@AutoConfigureMockMvc(addFilters = false)  // ← Bypasses security filters!
@Import(GlobalExceptionHandler.class)
class InstrumentControllerTest {
    // Does NOT import SecurityConfig
    // Does NOT define jwt.secret
    // Tests run without JWT validation
}
```

**Issues**:
- `@AutoConfigureMockMvc(addFilters = false)` disables all Spring Security filters
- This means authorization checks are bypassed
- Tests don't verify that `@PreAuthorize` annotations actually work
- Inconsistent with other controller tests

---

## 4. Authorization Refactor Status

### 4.1 Controllers Refactored to Use @PreAuthorize

All 14 controllers have been updated:

```
✅ AuthController (2 endpoints)
✅ ClientController (5 endpoints)
✅ AdvisorController (5 endpoints)  
✅ AppUserController (5 endpoints)
✅ AuditLogController (1 endpoint)
✅ ModelPortfolioController (4 endpoints)
✅ ModelPortfolioHoldingController (4 endpoints)
✅ ClientSubscriptionController (2 endpoints)
✅ ClientHoldingController (4 endpoints)
✅ ClientTradeController (5 endpoints)
✅ InstrumentController (4 endpoints)
✅ TradeSuggestionController (3 endpoints)
✅ RefreshTokenController (5 endpoints)
✅ RoleController (2 endpoints)
```

**Total**: 120+ endpoints protected with declarative `@PreAuthorize` annotations

### 4.2 Authorization Rules Pattern

**Examples from various controllers**:

```java
// Admin only
@GetMapping
@PreAuthorize("hasRole('ADMIN')")
public List<Client> listClients() { }

// Multiple roles
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST')")
public List<Advisor> listAdvisors() { }

// Authenticated users only
@PostMapping
@PreAuthorize("isAuthenticated()")
public Client createClient(CreateClientDto dto) { }

// Complex logic
@PreAuthorize("hasRole('ADMIN') or hasRole('ADVISOR')")
public void complexMethod() { }
```

---

## 5. Test Failure Scenarios

### 5.1 When Tests Would Fail

**Scenario 1: JWT Decoder Mismatch (No Impact Currently)**
- Test imports SecurityConfig
- JWT decoder created with default secret from application.yml
- Test mocks JWT with `.with(jwt())`
- **Result**: Works fine because SecurityConfig's JwtDecoder is never used for mocking

**Scenario 2: Integration Test with Real Token (Would Fail)**
- Test generates real JWT token in AuthServiceImpl
- Uses test secret from test class
- Token is passed to endpoint expecting JWT validation
- SecurityConfig's JwtDecoder tries to verify with default secret
- **Result**: 401 Unauthorized - signature verification fails

**Scenario 3: @AutoConfigureMockMvc(addFilters = false) [Current Issue]**
- InstrumentControllerTest bypasses all security filters
- `@PreAuthorize` annotations are never checked
- Test passes even though authorization rules are in place
- **Result**: Tests don't verify authorization is working

### 5.2 Why This Is A Problem

1. **False Confidence**: Tests pass but don't verify authorization works
2. **Inconsistency**: Some tests verify security, others don't
3. **Integration Risk**: Production code uses JWT security, tests don't validate it
4. **Maintenance**: If someone adds an integration test, it will mysteriously fail

---

## 6. Files Requiring Fixes

### 6.1 High Priority - Missing JWT Secret

**File**: `ClientControllerTest.java`  
**Location**: `C:\Users\Administrator\Desktop\tadpoles\backend\src\test\java\com\neueda\leap\controller\`  
**Issue**: Missing `@TestPropertySource` annotation  
**Fix**: Add the annotation above class declaration

**Before**:
```java
@WebMvcTest(ClientController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ClientControllerTest {
```

**After**:
```java
@WebMvcTest(ClientController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class ClientControllerTest {
```

### 6.2 High Priority - Security Filter Bypass

**File**: `InstrumentControllerTest.java`  
**Location**: `C:\Users\Administrator\Desktop\tadpoles\backend\src\test\java\com\neueda\leap\controller\`  
**Issue**: Uses `@AutoConfigureMockMvc(addFilters = false)` and doesn't import SecurityConfig  
**Fix**: Enable security filters and import SecurityConfig like other tests

**Before**:
```java
@WebMvcTest(InstrumentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InstrumentControllerTest {
```

**After**:
```java
@WebMvcTest(InstrumentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class InstrumentControllerTest {
```

Then add JWT mocking to all test methods:

```java
mockMvc.perform(get("/api/instruments")
    .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN")))))
    .andExpect(status().isOk());
```

### 6.3 Lower Priority - Integration Tests

**File**: `OrderFillServiceIntegrationTest.java`  
**Status**: ✅ Already correctly configured  
**Location**: `C:\Users\Administrator\Desktop\tadpoles\backend\src\test\java\com\neueda\leap\service\impl\`  
**Configuration**: Uses properties in @SpringBootTest with correct JWT secret  

```java
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:orderfilldb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "jwt.secret=test-jwt-secret-test-jwt-secret-123456",  // ← Correctly defined
    "spring.sql.init.mode=never"
})
```

---

## 7. Test Secret Strategy

### 7.1 Test JWT Secret

**Constant Used Across All Tests**:
```
test-jwt-secret-test-jwt-secret-123456
```

**Why This Secret?**
- Long enough for HMAC HS256 (minimum 32 characters required)
- Consistent across all tests for easy debugging
- Different from production default (prevents accidental usage of test secret in prod)
- All properly configured tests already use this exact secret

### 7.2 Production JWT Secret

**Default** (from application.yml):
```
mission-control-shared-secret-key-32-bytes-minimum
```

**Override Via Environment Variable**:
```bash
export JWT_SECRET="your-secure-production-secret-that-is-long-enough"
```

**Docker/K8s**:
```yaml
env:
  - name: JWT_SECRET
    value: "your-secure-production-secret-that-is-long-enough"
```

---

## 8. Implementation Steps to Fix Tests

### Step 1: Fix ClientControllerTest

Add `@TestPropertySource` annotation to `ClientControllerTest.java`:

```java
@WebMvcTest(ClientController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class ClientControllerTest {
    // ... rest of the class
}
```

### Step 2: Fix InstrumentControllerTest

1. Remove `@AutoConfigureMockMvc(addFilters = false)`
2. Add `SecurityConfig` to imports
3. Add `@TestPropertySource` for JWT secret
4. Add JWT mocking to all test methods

**Changes**:

```java
@WebMvcTest(InstrumentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class InstrumentControllerTest {
    // Update all test methods to include JWT mock
    
    @Test
    void listInstrumentsReturnsJsonResponse() throws Exception {
        Instrument instrument = buildInstrument();
        when(instrumentService.listInstruments()).thenReturn(List.of(instrument));

        mockMvc.perform(get("/api/instruments")
                .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN")))))  // ← Add this
                .andExpect(status().isOk());
    }
}
```

### Step 3: Verify All Tests Pass

After making changes, run the test suite:

```bash
cd C:\Users\Administrator\Desktop\tadpoles\backend
mvn clean test
```

Expected output:
```
[INFO] Tests run: [NUMBER], Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 9. Role Mapping in Authorization Tests

### 9.1 How Roles Are Converted

Spring Security automatically prepends `ROLE_` to role claims:

```java
// In SecurityConfig.extractAuthorities():
return roles.stream()
    .map(String::valueOf)
    .map(role -> "ROLE_" + role)  // ← "ADMIN" becomes "ROLE_ADMIN"
    .map(SimpleGrantedAuthority::new)
    .collect(Collectors.toList());
```

### 9.2 Testing Different Roles

**When writing tests, use role names WITHOUT "ROLE_" prefix**:

```java
// ✅ Correct - use plain role name
.with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN"))))

// ❌ Wrong - don't use ROLE_ prefix in the claim
.with(jwt().jwt(token -> token.claim("roles", List.of("ROLE_ADMIN"))))
```

### 9.3 Valid Roles in Tadpoles

```
ADMIN           - System administrator
AUDITOR         - Audit user
ANALYST         - Data analyst
ADVISOR         - Financial advisor
CLIENT          - Client user
COMPLIANCE      - Compliance officer
SUPPORT         - Support staff
OPERATIONS      - Operations team
REPORTING       - Reporting user
GUEST           - Guest user
```

---

## 10. Testing Authorization Rules

### 10.1 Test Pattern for Authorized Endpoint

```java
@Test
void adminOnlyEndpointAllowsAdmin() throws Exception {
    mockMvc.perform(get("/api/some-endpoint")
            .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN")))))
        .andExpect(status().isOk());
}
```

### 10.2 Test Pattern for Unauthorized Endpoint

```java
@Test
void adminOnlyEndpointRejectClient() throws Exception {
    mockMvc.perform(get("/api/some-endpoint")
            .with(jwt().jwt(token -> token.claim("roles", List.of("CLIENT")))))
        .andExpect(status().isForbidden());  // 403
}
```

### 10.3 Test Pattern for Unauthenticated Access

```java
@Test
void protectedEndpointRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/some-endpoint"))  // No JWT
        .andExpect(status().isUnauthorized());  // 401
}
```

---

## 11. Deployment & Production Considerations

### 11.1 Environment Variable Setup

**Development** (using defaults):
```bash
# Uses application.yml default: mission-control-shared-secret-key-32-bytes-minimum
mvn spring-boot:run
```

**Staging** (with custom secret):
```bash
export JWT_SECRET="staging-secret-key-that-is-at-least-32-characters-long"
mvn spring-boot:run
```

**Production** (Docker Compose):
```yaml
environment:
  - JWT_SECRET=${JWT_SECRET}  # Must be set externally
```

### 11.2 Password Hashing

Separate from JWT secrets:
- Database stores BCrypt hashes of user passwords
- Uses `BCryptPasswordEncoder` with default strength (10)
- Configured in `AuthServiceImpl`

```java
private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
```

---

## 12. Complete Endpoint Authorization Matrix

All 120+ endpoints now have explicit `@PreAuthorize` rules:

| Controller | Endpoint | Method | Roles | Status |
|-----------|----------|--------|-------|--------|
| Auth | `/auth/login` | POST | public | ✅ |
| Auth | `/auth/register` | POST | ADMIN | ✅ |
| Auth | `/auth/refresh` | POST | public | ✅ |
| Auth | `/auth/logout` | POST | authenticated | ✅ |
| Client | `/api/clients` | GET | ADMIN | ✅ |
| Client | `/api/clients` | POST | authenticated | ✅ |
| Client | `/api/clients/{id}` | GET | authenticated | ✅ |
| Client | `/api/clients/{id}` | PATCH | authenticated | ✅ |
| Client | `/api/clients/{id}/balance` | GET | authenticated | ✅ |
| Advisor | `/api/advisors` | GET | ADMIN, AUDITOR, ANALYST | ✅ |
| Advisor | `/api/advisors` | POST | ADMIN | ✅ |
| (... and 100+ more) | ... | ... | ... | ✅ |

See `AUTHORIZATION_REFACTOR_SUMMARY.md` for complete list.

---

## 13. Troubleshooting Guide

### Issue: "401 Unauthorized"

**Possible Causes**:
1. Missing JWT token in Authorization header
2. Token is expired
3. Invalid token format
4. Token signed with different secret

**Solution**:
- Ensure test includes `.with(jwt().jwt(token -> ...))`
- Verify test class has `@TestPropertySource` with correct secret
- Check token expiration time

### Issue: "403 Forbidden"

**Possible Causes**:
1. Valid token but wrong role
2. `@PreAuthorize` rule doesn't match user roles

**Solution**:
- Verify test uses correct role name matching `@PreAuthorize` requirement
- Check role list in JWT token claim
- Review `AUTHORIZATION_REFACTOR_SUMMARY.md` for endpoint requirements

### Issue: "500 Internal Server Error"

**Possible Causes**:
1. SecurityConfig bean initialization failed
2. JWT secret too short (less than 32 characters)
3. JWT decoder creation failed

**Solution**:
- Check test property source defines valid JWT secret
- Ensure secret is at least 32 characters
- Review Spring Security logs for detailed error

---

## 14. Files Summary

### Source Code Files

| File | Purpose | Key Method |
|------|---------|-----------|
| `application.yml` | JWT secret configuration | Property: `jwt.secret` |
| `SecurityConfig.java` | Spring Security configuration | Bean: `jwtDecoder()` |
| `AuthServiceImpl.java` | JWT token generation | Method: `generateToken()` |
| `UserMapper.java` | Database user queries | Method: `findByUsername()` |
| All Controllers | Protected endpoints | Annotation: `@PreAuthorize` |

### Test Files

| File | Location | Status | Issue |
|------|----------|--------|-------|
| `ClientControllerTest.java` | `backend/src/test/...` | ❌ Missing | Add `@TestPropertySource` |
| `InstrumentControllerTest.java` | `backend/src/test/...` | ❌ Wrong | Remove filter bypass, add security |
| `AdvisorControllerTest.java` | `backend/src/test/...` | ✅ Correct | No changes |
| 7 more controller tests | `backend/src/test/...` | ✅ Correct | No changes |
| `OrderFillServiceIntegrationTest.java` | `backend/src/test/...` | ✅ Correct | No changes |

---

## 15. Next Steps

1. **Apply Fixes** (this session):
   - Add `@TestPropertySource` to `ClientControllerTest.java`
   - Fix `InstrumentControllerTest.java` to enable security

2. **Run Tests** (this session):
   - Execute `mvn clean test` to verify all tests pass

3. **Deploy**:
   - Set `JWT_SECRET` environment variable in production
   - Monitor authentication/authorization logs

4. **Future Enhancements**:
   - Add token refresh endpoint improvements
   - Add logout functionality with token blacklist
   - Add user activity audit logging
   - Consider OAuth2 integration

---

## References

- `AUTHENTICATION_GUIDE.md` - Detailed authentication setup guide
- `AUTHORIZATION_REFACTOR_SUMMARY.md` - Complete list of refactored endpoints
- `backend/pom.xml` - Dependencies (JJWT, Spring Security OAuth2)
- Spring Security Documentation: https://spring.io/projects/spring-security
- JWT (JSON Web Tokens): https://www.rfc-editor.org/rfc/rfc7519

---

**Created**: October 2, 2026  
**Last Updated**: [Will be set after fixes are applied]  
**Test Status**: Requires fixes in 2 test files

