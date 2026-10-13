# Holdings and Trades Implementation Notes

## What was implemented

This work adds full backend support for:

- `ClientHoldings`
- `ClientTrades`
- `OrderFillService`

The implementation follows the same project pattern already used for `Client` and `Instrument`:

- domain model
- request/response DTOs
- not-found exceptions
- MyBatis mapper layer
- service interface
- service implementation
- REST controller
- controller tests
- service tests

It also adds transaction-backed order filling so the three required updates happen together:

1. client holding update
2. client cash balance update
3. permanent trade record update

If any of those steps fail, the transaction rolls back and none of them are kept.

---

## New API areas

### Holdings endpoints

Base path:
- `/api/clients/{clientId}/holdings`

Endpoints added:
- `GET /api/clients/{clientId}/holdings`
- `POST /api/clients/{clientId}/holdings`
- `GET /api/clients/{clientId}/holdings/{holdingId}`
- `PATCH /api/clients/{clientId}/holdings/{holdingId}`

### Trades endpoints

Base path:
- `/api/clients/{clientId}/trades`

Endpoints added:
- `GET /api/clients/{clientId}/trades`
- `POST /api/clients/{clientId}/trades`
- `GET /api/clients/{clientId}/trades/{tradeId}`
- `PATCH /api/clients/{clientId}/trades/{tradeId}`
- `POST /api/clients/{clientId}/trades/{tradeId}/fill`

---

## Main design choices

## 1. Follow the same structure as `Client`

The new code mirrors the existing project style instead of introducing a different architecture.

That keeps the codebase easier to read and easier to split across developers.

## 2. Keep `Instrument` consistent

I reviewed the `Instrument` layer.

Result:
- it already follows the same pattern as `Client`
- no structural change was needed

It is now used as a validation dependency by the new holdings and trades services.

## 3. Put the all-or-nothing business rule in one service

The atomic requirement is enforced in:
- `OrderFillService`
- `OrderFillServiceImpl`

This service is marked transactional.

That is the key part of the requirement.

The domain classes hold data, but the transaction service controls the business rule.

---

## Files added

### Controllers
- `backend/src/main/java/com/neueda/leap/controller/ClientHoldingController.java`
- `backend/src/main/java/com/neueda/leap/controller/ClientTradeController.java`

### DTOs
- `backend/src/main/java/com/neueda/leap/dto/CreateClientHoldingRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateClientHoldingRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ClientHoldingResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ClientHoldingListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/CreateClientTradeRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/UpdateClientTradeRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ClientTradeResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/ClientTradeListResponseDto.java`
- `backend/src/main/java/com/neueda/leap/dto/FillOrderRequestDto.java`
- `backend/src/main/java/com/neueda/leap/dto/OrderFillResponseDto.java`

### Exceptions
- `backend/src/main/java/com/neueda/leap/exception/ClientHoldingNotFoundException.java`
- `backend/src/main/java/com/neueda/leap/exception/ClientTradeNotFoundException.java`

### Mappers
- `backend/src/main/java/com/neueda/leap/mapper/ClientHoldingMapper.java`
- `backend/src/main/java/com/neueda/leap/mapper/ClientTradeMapper.java`

### Service interfaces
- `backend/src/main/java/com/neueda/leap/service/ClientHoldingService.java`
- `backend/src/main/java/com/neueda/leap/service/ClientTradeService.java`
- `backend/src/main/java/com/neueda/leap/service/OrderFillService.java`

### Service implementations
- `backend/src/main/java/com/neueda/leap/service/impl/ClientHoldingServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/ClientTradeServiceImpl.java`
- `backend/src/main/java/com/neueda/leap/service/impl/OrderFillServiceImpl.java`

### Tests
- `backend/src/test/java/com/neueda/leap/controller/ClientHoldingControllerTest.java`
- `backend/src/test/java/com/neueda/leap/controller/ClientTradeControllerTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/ClientHoldingMapperTest.java`
- `backend/src/test/java/com/neueda/leap/mapper/ClientTradeMapperTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/ClientHoldingServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/ClientTradeServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/OrderFillServiceImplTest.java`
- `backend/src/test/java/com/neueda/leap/service/impl/OrderFillServiceIntegrationTest.java`

### Test SQL resources
- `backend/src/test/resources/orderfill/schema.sql`
- `backend/src/test/resources/orderfill/data.sql`
- `backend/src/test/resources/mapper/schema.sql`
- `backend/src/test/resources/mapper/data.sql`

---

## Files updated

### `ClientMapper`
Updated to support order filling:
- row lock fetch for a client during fill
- direct cash balance update during fill

### `GlobalExceptionHandler`
Updated to handle:
- `ClientHoldingNotFoundException`
- `ClientTradeNotFoundException`

---

## What `OrderFillService` does

When a trade is filled, the service:

1. validates the client id and trade id
2. loads and locks the trade row
3. checks that the trade belongs to the requested client
4. allows fill only for `PENDING` or `APPROVED` trades
5. loads and locks the client row
6. loads the instrument and checks that it is active
7. loads and locks the current holding for the traded instrument
8. calculates the cash movement
9. calculates the new holding quantity
10. updates or inserts the holding
11. updates the client cash balance
12. updates the trade record to `EXECUTED`
13. commits only if all steps succeed

### Buy behavior
- decreases client cash
- increases holding quantity
- marks trade as executed

### Sell behavior
- increases client cash
- decreases holding quantity
- if quantity reaches zero, the holding row is removed instead of storing an invalid zero quantity
- marks trade as executed

---

## Validation added

### Holdings validation
- client id must be positive
- holding id must be positive
- instrument id must be positive
- quantity must be greater than zero
- `asOfDate` is required for create
- update requires at least one field

### Trades validation
- client id must be positive
- trade id must be positive
- instrument id must be positive
- trade type must be `BUY` or `SELL`
- quantity must be greater than zero
- price must be greater than zero
- trade date is required
- status must be one of:
  - `PENDING`
  - `APPROVED`
  - `REJECTED`
  - `EXECUTED`
- update requires at least one field

### Order fill validation
- trade must exist
- trade must belong to the requested client
- trade must be `PENDING` or `APPROVED`
- instrument must exist
- instrument must be active
- buy fill requires enough cash
- sell fill requires enough holdings
- optional execution price must be positive
- optional approver id must be positive

---

## Test approach

This was built test-first.

### Unit tests added

#### Holdings
- list holdings
- get holding
- create holding
- update holding
- missing holding returns not found
- invalid payloads return validation errors

#### Trades
- list trades
- get trade
- create trade
- default trade status to `PENDING`
- update trade
- missing trade returns not found
- invalid trade type rejected
- empty update rejected

#### Order fill
- buy fill updates cash, holding, and trade state
- sell fill updates cash and holding correctly
- missing trade is rejected
- insufficient cash is rejected
- insufficient holdings are rejected
- already executed trade is rejected

### Integration test added

A Spring Boot integration test uses H2 and SQL scripts to verify real transaction behavior.

It checks two cases:

1. successful fill commits all changes
2. a failure during trade-record update rolls everything back

That second test is the strongest proof for the business requirement.

### Mapper tests added

The MyBatis layer now has direct tests for both new mappers so they follow the same pattern already used by `ClientMapper` and `InstrumentMapper`.

Those tests verify:

- select by id / list behavior
- insert behavior with generated ids
- update behavior
- order-fill helper queries used by the transaction flow

---

## Business requirement coverage

Requirement:

> When an order is filled, the client's holding, their cash balance and the permanent trade record must all update together. None of the three may update without the others, even if the platform fails partway through.

This implementation satisfies that by putting the fill logic in one transactional service method.

If any step fails:
- holding change is rolled back
- cash balance change is rolled back
- trade record change is rolled back

---

## Notes for other developers

### Lowest-overlap work areas now

#### Holdings owner
Can work mainly in:
- `ClientHoldingController`
- `ClientHoldingService`
- `ClientHoldingServiceImpl`
- `ClientHoldingMapper`
- holding DTOs

#### Trades owner
Can work mainly in:
- `ClientTradeController`
- `ClientTradeService`
- `ClientTradeServiceImpl`
- `ClientTradeMapper`
- trade DTOs

#### Order execution owner
Can work mainly in:
- `OrderFillService`
- `OrderFillServiceImpl`
- integration tests

#### Error handling owner
Can work mainly in:
- `GlobalExceptionHandler`
- new exception classes

This keeps responsibilities separated and avoids large merge overlap.

---

## Verification

Verified by running:

```cmd
cd /d C:\Users\Administrator\Documents\tadpoles\backend
mvn -q test
```

The backend test suite passed after the changes.


