# Walk-In Order Feature — Implementation Prompt

I need to implement a Walk-In Order feature for my Spring Boot ecommerce backend. This is a Point of Sale feature that allows an admin to place orders on behalf of customers who walk into the physical store. Here is everything you need to know to implement this feature end to end.

---

## Context and Background

My application already has the following implemented and working:

- Product management with stock tracking
- Online order flow with Paystack payment integration
- Coupon and discount system
- Tax calculation service via a `TaxService` that returns a `TaxCalculationResult` containing a list of `OrderTax` objects and a `totalTaxAmount`
- Double entry bookkeeping system via a `BookkeepingService` with a `JournalEntry`, `JournalEntryLine` and `LedgerAccount` system
- Authentication via JWT stored in httpOnly cookies with a `AuthUserUtil` helper to get the current logged in user
- A `Money` embeddable class that wraps a `BigDecimal amount` and a `CurrencyCode` enum
- A `CustomApiResponse` wrapper for all API responses
- A `PageResponse` wrapper for paginated responses
- A `PaginationUtil` helper for building page responses
- Global exception handling with `ResourceNotFoundException` and `BadRequestException`

---

## Feature Overview

A walk-in order is placed entirely by the admin through a dedicated POS screen. There is no cart, no payment gateway and no async webhook. Payment is collected in person and the order is confirmed instantly. Stock is deducted immediately at the point of order creation.

---

## Order Number Generation

Walk-in orders must have a distinct order number that differentiates them from online orders. Online orders use the prefix `ORD`. Walk-in orders must use the prefix `WLK`.

The format is:
```
WLK-{yyyyMMddHHmmss}-{8 character uppercase UUID snippet}
Example: WLK-20260301105423-A3F2B1C4
```

The timestamp includes hours, minutes and seconds unlike the online order generator because multiple walk-in orders can be placed within the same day and even the same minute during busy periods. The generator must check the database to ensure uniqueness and retry up to 5 times before throwing an `IllegalStateException`. It must be annotated as a Spring `@Component` and follow the same pattern as the existing `OrderNumberGenerator`.

---

## New Entities Required

### WalkInCustomer

This entity represents a customer who does not have a registered account. It lives in its own table called `walk_in_customers`. Fields required are a Long `id`, a nullable String `name`, a nullable String `phone`, a nullable String `email`, a non-nullable Boolean `isAnonymous` defaulting to false, and a `createdAt` timestamp managed by `@CreatedDate`. This entity uses Lombok `@Builder`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor` and JPA `@EntityListeners(AuditingEntityListener.class)`.

### WalkInOrder

This is the main order entity stored in a table called `walk_in_orders`. Fields required are:

- Long `id`
- String `orderNumber` — unique and non-nullable
- A `@ManyToOne` lazy reference to `User` named `registeredUser` with join column `registered_user_id` — nullable, populated when a registered customer is found
- A `@ManyToOne` lazy reference to `WalkInCustomer` named `walkInCustomer` with join column `walk_in_customer_id` — nullable, populated when an unregistered or anonymous customer is used
- A `@ManyToOne` lazy reference to `User` named `processedBy` with join column `processed_by_id` — non-nullable, always the admin who placed the order
- A `@OneToMany` cascade all orphan removal list of `WalkInOrderItem` mapped by `walkInOrder`
- A `@OneToMany` cascade all list of `WalkInOrderTax` mapped by `walkInOrder`
- An `@Embedded` `Money` field named `subtotal`
- An `@Embedded` `Money` field named `discountAmount`
- An `@Embedded` `Money` field named `totalTaxAmount`
- An `@Embedded` `Money` field named `totalAmount`
- An `@Embedded` `Money` field named `amountPaid` — the actual amount handed over by the customer
- An `@Embedded` `Money` field named `changeGiven` — change returned to the customer, zero for non-cash payments
- A `@ManyToOne` nullable reference to `Coupon`
- A `@Enumerated(EnumType.STRING)` non-nullable `WalkInPaymentMethod` enum field named `paymentMethod`
- A nullable `BigDecimal` named `splitCashAmount` — only populated for SPLIT payment method
- A nullable `BigDecimal` named `splitMobileAmount` — only populated for SPLIT payment method
- A `@Enumerated(EnumType.STRING)` non-nullable `WalkInOrderStatus` enum field named `status`
- A non-nullable Boolean `receiptPrinted` defaulting to false
- A `@CreatedDate` non-nullable non-updatable `createdAt` timestamp
- A `@LastModifiedDate` `updatedAt` timestamp

### WalkInOrderItem

Stored in a table called `walk_in_order_items`. Fields required are:

- Long `id`
- A `@ManyToOne` lazy reference to `WalkInOrder` with join column `walk_in_order_id` — non-nullable
- Long `productId` — non-nullable, a direct ID reference not a foreign key relationship
- String `productName` — snapshot of name at time of sale
- String `sku` — snapshot of SKU at time of sale
- Integer `quantity` — non-nullable
- `BigDecimal` `unitPrice` — snapshot of selling price at time of sale, precision 19 scale 2
- `BigDecimal` `costPrice` — snapshot of cost price at time of sale, precision 19 scale 2
- `BigDecimal` `totalPrice` — unitPrice multiplied by quantity, precision 19 scale 2

### WalkInOrderTax

Stored in a table called `walk_in_order_taxes`. Mirrors the existing `OrderTax` entity structure but with a `@ManyToOne` reference to `WalkInOrder` instead of `Order`. Fields are Long `id`, `@ManyToOne` lazy `WalkInOrder` with join column `walk_in_order_id`, String `taxName`, String `taxCode`, `BigDecimal` `taxRate`, `BigDecimal` `taxableAmount` and `BigDecimal` `taxAmount`. All monetary fields use precision 19 scale 2.

---

## New Enums Required

### WalkInPaymentMethod
Values: `CASH`, `MOBILE_MONEY`, `CARD`, `SPLIT`

### WalkInOrderStatus
Values: `COMPLETED`, `CANCELLED`, `REFUNDED`

---

## New Ledger Accounts to Seed

A new asset account must be seeded in the `ChartOfAccountsSeeder` for mobile money since it is a distinct asset from physical cash:

- Code: `1001`, Name: `Mobile Money`, Type: `ASSET`, Category: `MOBILE_MONEY`

The `AccountCategory` enum must be updated to include `MOBILE_MONEY`.

The `JournalEntryType` enum must be updated to include `WALK_IN_SALE`.

---

## DTOs Required

### WalkInCustomerRequest
Fields: nullable String `name`, nullable String `phone`, nullable String `email`. No validation annotations since all fields are optional for anonymous walk-ins.

### WalkInOrderItemRequest
Fields: non-nullable Long `productId` annotated with `@NotNull`, non-nullable Integer `quantity` annotated with `@NotNull` and must be greater than zero.

### WalkInOrderRequest
Fields:
- nullable Long `registeredUserId`
- nullable `WalkInCustomerRequest` `walkInCustomer`
- non-nullable non-empty list of `WalkInOrderItemRequest` annotated with `@NotNull` and `@NotEmpty`
- nullable String `couponCode`
- non-nullable `WalkInPaymentMethod` `paymentMethod` annotated with `@NotNull`
- non-nullable `BigDecimal` `amountPaid` annotated with `@NotNull`
- nullable `BigDecimal` `splitCashAmount`
- nullable `BigDecimal` `splitMobileAmount`

### WalkInOrderResponse
Fields: String `orderNumber`, String `customerName`, String `customerPhone`, String `processedBy`, `WalkInPaymentMethod` `paymentMethod`, `WalkInOrderStatus` `status`, String `subtotal`, String `discountAmount`, String `totalTaxAmount`, String `totalAmount`, String `amountPaid`, String `changeGiven`, Boolean `receiptPrinted`, `LocalDateTime` `createdAt`, list of `WalkInOrderItemResponse`.

### WalkInOrderItemResponse
Fields: String `productName`, String `sku`, Integer `quantity`, String `unitPrice`, String `totalPrice`.

### CustomerSearchResponse
Fields: Long `id`, String `fullName`, String `email`, String `phone`. Used when admin searches for a registered customer by name or email.

---

## Service Layer

### WalkInOrderService Interface
Must declare the following methods:

- `placeWalkInOrder(WalkInOrderRequest request)` returning `WalkInOrderResponse`
- `searchCustomers(String query)` returning `List<CustomerSearchResponse>`
- `getWalkInOrders(LocalDate date, Pageable pageable)` returning `PageResponse<WalkInOrderResponse>`
- `getWalkInOrder(String orderNumber)` returning `WalkInOrderResponse`
- `markReceiptPrinted(String orderNumber)` returning void

### WalkInOrderServiceImpl

Must be annotated with `@Service`, `@RequiredArgsConstructor`, `@Slf4j` and `@Transactional`.

The `placeWalkInOrder` method must execute the following steps in strict order:

1. Get the current logged in admin using `authUserUtil.getCurrentUser()`
2. Resolve the customer using a private `resolveCustomer` helper method. If `registeredUserId` is present fetch the user from `UserRepository` and set as `registeredUser`. If `walkInCustomer` request is present create and save a `WalkInCustomer` entity. If both are null create an anonymous `WalkInCustomer` with `isAnonymous` set to true and save it.
3. Build order items using a private `buildOrderItems` helper. For each item fetch the product from `ProductRepository`, validate it is active, validate stock quantity is sufficient, snapshot the name, SKU, selling price and cost price, and calculate total price.
4. Calculate subtotal by summing all item total prices.
5. Apply coupon if `couponCode` is present. Use the existing coupon validation and discount calculation logic. Call `updateCouponUsage` after applying.
6. Calculate taxes by passing the price after discount to `TaxService.calculateTaxes`.
7. Calculate total amount as price after discount plus total tax amount.
8. Validate payment using a private `validatePayment` helper. For `CASH` payment the `amountPaid` must be greater than or equal to total. For `SPLIT` payment the sum of `splitCashAmount` and `splitMobileAmount` must be greater than or equal to total. For `MOBILE_MONEY` and `CARD` the amount paid must equal the total.
9. Calculate change as `amountPaid` minus `totalAmount` with a minimum of zero using `BigDecimal.ZERO` as floor.
10. Set all financial fields on the order using the `Money` embeddable.
11. Link tax lines to the order by iterating `taxResult.orderTaxes()` and setting the `walkInOrder` reference on each.
12. Call a private `reserveAndSellStock` helper that for each order item fetches the product, deducts the quantity from `stockQuantity` and increments `soldCount`, then saves.
13. Save the order using `WalkInOrderRepository`.
14. Call `bookkeepingService.recordWalkInSale(savedOrder)`.
15. Log the order number and admin email.
16. Return the mapped response.

The `searchCustomers` method must query `UserRepository` for users whose `fullName` or `email` contains the query string and have the `CUSTOMER` role. The query should be case insensitive.

---

## Bookkeeping Integration

A new method `recordWalkInSale(WalkInOrder order)` must be added to `BookkeepingService`. This method must create a balanced journal entry with the following lines:

For all payment methods the revenue and COGS lines are identical to an online sale:
- DEBIT `COGS` for total cost of all items
- CREDIT `INVENTORY` for total cost of all items
- CREDIT `SALES_REVENUE` for subtotal minus discount
- CREDIT `TAX_PAYABLE` for total tax amount if greater than zero
- DEBIT `DISCOUNT_EXPENSE` for discount amount if greater than zero
- CREDIT corresponding cash account for discount offset if discount greater than zero

The cash debit line differs by payment method:
- `CASH` — DEBIT `CASH` for total amount
- `MOBILE_MONEY` — DEBIT `MOBILE_MONEY` for total amount
- `CARD` — DEBIT `CASH` for total amount (card settlements go to cash account for simplicity)
- `SPLIT` — DEBIT `CASH` for `splitCashAmount` AND DEBIT `MOBILE_MONEY` for `splitMobileAmount`

The journal entry description must include the order number, the admin name from `processedBy` and the payment method. The `referenceType` must be the string `WALK_IN_ORDER` and `referenceId` must be the order number. The `JournalEntryType` must be `WALK_IN_SALE`.

---

## Repository Layer

### WalkInOrderRepository
Must extend `JpaRepository<WalkInOrder, Long>` and include:
- `Optional<WalkInOrder> findByOrderNumber(String orderNumber)`
- A query to find all walk-in orders filtered optionally by date, returning a `Page<WalkInOrder>` with a `Pageable` parameter. When date is null return all orders. When date is provided filter by `createdAt` between start of day and end of day. Order by `createdAt` descending.
- `boolean existsByOrderNumber(String orderNumber)` — used by the order number generator

### WalkInCustomerRepository
Must extend `JpaRepository<WalkInCustomer, Long>`. No custom queries needed.

### UserRepository addition
Must add a method to search users by full name or email containing a query string with case insensitive matching, filtered to only return users with the `CUSTOMER` role.

---

## Controller Layer

Must be annotated with `@RestController`, `@RequestMapping("/api/v1/admin/walk-in")`, `@RequiredArgsConstructor` and `@Slf4j`. All endpoints must be secured to `ADMIN` role only via Spring Security configuration.

Endpoints:

- `POST /order` — place a walk-in order, accepts `@Valid @RequestBody WalkInOrderRequest`, returns `WalkInOrderResponse`
- `GET /customers/search` — search registered customers, accepts `@RequestParam String query`, returns `List<CustomerSearchResponse>`
- `GET /orders` — get all walk-in orders, accepts optional `@RequestParam LocalDate date` and `Pageable`, returns `PageResponse<WalkInOrderResponse>`
- `GET /orders/{orderNumber}` — get a single walk-in order by order number, returns `WalkInOrderResponse`
- `PATCH /orders/{orderNumber}/receipt-printed` — mark receipt as printed, returns void wrapped in `CustomApiResponse`

---

## Mapper

A `WalkInOrderMapper` class must map `WalkInOrder` to `WalkInOrderResponse`. The `customerName` field must be resolved by checking `registeredUser` first, then `walkInCustomer.name`, then defaulting to `Anonymous` if both are null. The `customerPhone` field must follow the same resolution order checking `registeredUser` profile phone, then `walkInCustomer.phone`, then defaulting to `N/A`. All monetary fields must be formatted as `GHS {amount}` strings. The `processedBy` field must be the full name of the admin from `processedBy.getFullName()`.

---

## Security

The `/api/v1/admin/walk-in/**` path pattern must be added to the list of admin-only secured endpoints in the Spring Security configuration alongside the existing `/api/v1/admin/**` pattern.

---

## Additional Notes

- The walk-in order flow must never interact with the `Cart` entity
- Walk-in orders must never go through `PaymentStatus` lifecycle states — they are always created with `WalkInOrderStatus.COMPLETED` directly
- Stock deduction must happen synchronously within the same transaction as order creation — there is no reservation phase separate from the sale phase unlike online orders
- The `changeGiven` field must always be zero for `MOBILE_MONEY` and `CARD` payments
- If `amountPaid` exactly equals `totalAmount` for a `CASH` payment then `changeGiven` is zero
- All `BigDecimal` monetary fields must use `RoundingMode.HALF_UP` and scale of 2
- The `WalkInOrderNumberGenerator` must follow the exact same structure as the existing `OrderNumberGenerator` including the `MAX_ATTEMPTS` constant and `IllegalStateException` on exhaustion
