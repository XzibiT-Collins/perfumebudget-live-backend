# Detailed Implementation Plan: Consolidated Sales Analytics Endpoint

## Summary

Implement one admin analytics endpoint that returns the analytics page payload for a selected sales source and timeline granularity.

This endpoint must support:

- source=ALL for consolidated totals across online and walk-in sales
- source=ONLINE for ecommerce-only totals
- source=WALK_IN for POS-only totals
- granularity=DAY|WEEK|MONTH|YEAR for chart grouping

The endpoint will return:

- revenueBreakdown for the area chart
- miniStats for the summary cards
- orderStatus for the pie chart

Use bookkeeping journal entries as the source of truth for revenue and order tables as the source of truth for order counts and statuses.

## API Contract

Add a new admin endpoint under the existing metrics controller:

- GET /api/v1/admin/metrics/sales-analytics

Query parameters:

- source
    - enum: ALL, ONLINE, WALK_IN
    - default: ALL
- granularity
    - enum: DAY, WEEK, MONTH, YEAR
    - default: DAY
- from
    - optional LocalDate
- to
    - optional LocalDate

Validation rules:

- if only one of from or to is provided, reject with 400
- if from > to, reject with 400
- if from/to are absent, derive a default range from granularity
- apply date filters inclusively:
    - start = from.atStartOfDay()
    - end = to.atTime(23,59,59)

Response shape:

interface RevenueDataPoint {
name: string;
revenue: number;
}

interface MiniStat {
label: string;
value: string;
trend: string;
isUp: boolean;
}

interface OrderStatusDatum {
name: string;
value: number;
}

interface SalesAnalyticsResponse {
revenueBreakdown: RevenueDataPoint[];
miniStats: MiniStat[];
orderStatus: OrderStatusDatum[];
}

Wrap in the existing API envelope:

{
"description": null,
"data": {
"revenueBreakdown": [],
"miniStats": [],
"orderStatus": []
}
}

## Data Source Rules

Revenue source:

- use JournalEntry + JournalEntryLine
- count only sales revenue credits
- include only:
    - JournalEntryType.SALE for online sales
    - JournalEntryType.WALK_IN_SALE for walk-in sales
- count only lines where:
    - account.category = SALES_REVENUE
    - entryType = CREDIT

Why:

- online orders become revenue only after successful payment and bookkeeping entry creation
- walk-in orders are recorded directly into bookkeeping
- this gives one consistent revenue source for ALL

Order count source:

- Order table for online
- WalkInOrder table for walk-in

Status source:

- Order.deliveryStatus for online operational statuses
- WalkInOrder.status for walk-in statuses

## Defaults and Time Window Rules

Default ranges when from and to are absent:

- DAY: last 7 calendar days including today
- WEEK: last 8 calendar weeks including current week
- MONTH: last 12 calendar months including current month
- YEAR: last 5 calendar years including current year

Bucket rules:

- DAY
    - one bucket per calendar date
    - label example: Mon
- WEEK
    - one bucket per ISO-style week start
    - label example: Feb 18
    - use the start date of the bucket as the label source
- MONTH
    - one bucket per calendar month
    - label example: Jan
- YEAR
    - one bucket per calendar year
    - label example: 2026

Zero-fill rules:

- generate the full expected bucket list first
- merge DB results into buckets
- any missing bucket must return revenue: 0
- preserve chronological ordering

## Public Types and Enums to Add

Add analytics-specific DTOs and enums:

- SalesAnalyticsResponse
- RevenueDataPointResponse
- MiniStatResponse
- OrderStatusSliceResponse
- SalesAnalyticsSource
- SalesAnalyticsGranularity

Recommended enum values:

public enum SalesAnalyticsSource {
ALL,
ONLINE,
WALK_IN
}

public enum SalesAnalyticsGranularity {
DAY,
WEEK,
MONTH,
YEAR
}

## Controller Changes

In C:/Users/huvis/OneDrive/Desktop/Projects/Vestrex%20Commerce/perfume_budget/src/main/java/com/example/perfume_budget/controller/AdminMetricController.java:

- add GET /sales-analytics
- bind:
    - source
    - granularity
    - from
    - to
- call a new service method on AdminMetricService
- return CustomApiResponse.success(...)

Controller behavior:

- keep the endpoint admin-only under the existing class-level authorization
- avoid putting analytics construction logic in the controller
- only parse input and delegate

## Service Interface Changes

In AdminMetricService:

- add a method like:

SalesAnalyticsResponse getSalesAnalytics(
SalesAnalyticsSource source,
SalesAnalyticsGranularity granularity,
LocalDate from,
LocalDate to
);

If from and to remain optional in the controller, allow nullable inputs in the implementation and resolve defaults there.

## Service Implementation Plan

In C:/Users/huvis/OneDrive/Desktop/Projects/Vestrex%20Commerce/perfume_budget/src/main/java/com/example/perfume_budget/service/AdminMetricServiceImpl.java:

Add a new getSalesAnalytics(...) implementation that orchestrates:

1. request normalization
2. date range resolution
3. revenue aggregation
4. previous-period aggregation for trend
5. order count aggregation
6. order status aggregation
7. response assembly

### Step 1: Normalize Inputs

- if source == null, use ALL
- if granularity == null, use DAY
- resolve the effective date window
- compute:
    - currentStart
    - currentEnd
    - previousStart
    - previousEnd

Previous-period rule:

- previous window length must match the selected current window exactly
- examples:
    - if current is 7 days, previous is the preceding 7 days
    - if current is 8 weeks, previous is the preceding 8 weeks
    - if current is 12 months, previous is the preceding 12 months

### Step 2: Aggregate Revenue

- fetch grouped revenue totals from journal data for the current range
- filter sale types by source
- zero-fill into the bucket list
- map each bucket to:
    - name
    - revenue

### Step 3: Calculate Mini Stats

Build exactly three cards:

1. Avg Order Value

- numerator = selected revenue total
- denominator = selected order count
- if denominator is zero, return GHS 0.00

2. Total Revenue

- use the selected revenue total
- format with existing Money(...).toString()

3. Total Orders

- ONLINE: completed online order count in range
- WALK_IN: completed walk-in order count in range
- ALL: sum of both

Trend calculation:

- compare current value vs previous-period value for each mini-stat
- trend format should be plain percentage string without sign duplication:
    - "12%"
    - "0%"
- isUp = true when current >= previous
- isUp = false when current < previous`

Trend formula:

- if previous > 0:
    - ((current - previous) / previous) * 100
- if previous == 0`:
    - if current == 0, return 0%, isUp = true`
    - if current > 0, return 100%, isUp = true`
- round to a predictable scale before formatting, then strip trailing decimals if desired

### Step 4: Build Order Status Pie Data

For ONLINE:

- aggregate from Order.deliveryStatus

Recommended labels:

- Pending
- Packing
- Out for Delivery
- Delivered
- Cancelled

For WALK_IN:

- aggregate from WalkInOrder.status

Recommended labels:

- Completed
- Cancelled
- Refunded

For ALL:

- return a combined normalized set, not separate source-prefixed labels
- recommended combined labels:
    - Completed
    - Pending
    - Packing
    - Out for Delivery
    - Delivered
    - Cancelled
    - Refunded

Normalization rules for ALL:

- online DELIVERED contributes to Delivered
- online PENDING contributes to Pending
- online PACKING contributes to Packing
- online OUT_FOR_DELIVERY contributes to Out for Delivery
- online CANCELLED contributes to Cancelled
- walk-in COMPLETED contributes to Completed
- walk-in CANCELLED contributes to Cancelled
- walk-in REFUNDED contributes to Refunded

Do not try to force walk-in completed orders into online Delivered. Keep them separate because they represent different business states.

## Repository Changes

Add analytics-specific queries rather than overloading existing order repository methods.

### Journal Repository

In C:/Users/huvis/OneDrive/Desktop/Projects/Vestrex%20Commerce/perfume_budget/src/main/java/com/example/perfume_budget/repository/JournalEntryRepository.java:

- add grouped revenue queries for:
    - day buckets
    - week buckets
    - month buckets
    - year buckets

Use projections that return:

- bucket key
- aggregated revenue

Because JPQL date bucketing is limited and DB-specific, choose one of these approaches and lock it in:

Recommended approach:

- use native SQL against the current PostgreSQL-oriented schema
- use DATE_TRUNC('day'|'week'|'month'|'year', j.transaction_date)
- join journal_entries and journal_entry_lines
- filter by:
    - j.type in (...)
    - l.entry_type = 'CREDIT'
    - account category for sales revenue
    - j.transaction_date between :start and :end

This avoids fragile JPQL casting logic and makes week/month/year grouping correct.

Add a small projection interface or DTO for grouped results, for example:

- RevenueBucketProjection
    - LocalDateTime getBucketStart()
    - BigDecimal getRevenue()

### Order Repository

Add count and grouped status queries for online analytics:

- count completed online orders in date range
- count online orders by delivery status in date range

Online count rule:

- only count online orders with status = COMPLETED for mini-stats totals
- for orderStatus, use all relevant online orders in the range unless you explicitly want only paid orders
- recommended default: status pie should reflect actual orders created in the period, regardless of payment completion only if the product wants operational status visibility
- if the pie chart is intended to represent sales only, then filter online statuses to PaymentStatus.COMPLETED
- to keep the implementation decision-complete, use:
    - miniStats.Total Orders: count only revenue-eligible orders
    - orderStatus: count all orders created in the period by delivery status

### Walk-In Order Repository

Add:

- count completed walk-in orders in date range
- count walk-in orders by walk-in status in date range

Walk-in count rule:

- completed walk-in orders count toward Total Orders
- orderStatus uses all walk-in orders created in the range

## Required Projections

Add minimal projections for grouped repository results:

- RevenueBucketProjection
- OrderStatusCountProjection

Suggested OrderStatusCountProjection:

public interface OrderStatusCountProjection {
String getStatus();
Long getCount();
}

For online and walk-in, separate projections are acceptable if enum-to-string handling is easier.

## Assembly Logic

Create small private helpers inside the service or a dedicated helper class.

Recommended helpers:

- ResolvedAnalyticsWindow resolveWindow(granularity, from, to)
- List<RevenueDataPointResponse> buildRevenueBreakdown(...)
- List<MiniStatResponse> buildMiniStats(...)
- List<OrderStatusSliceResponse> buildOrderStatus(...)
- BigDecimal calculateTrendPercent(current, previous)
- String formatTrend(BigDecimal percent)
- String labelForBucket(granularity, bucketStart)

Do not mix SQL result parsing, date math, and frontend label formatting in one method.

## Exact Metric Semantics

Lock these semantics so implementation does not guess:

Revenue:

- recognized revenue only
- from SALES_REVENUE credit lines only
- excludes tax
- excludes discounts as standalone lines
- excludes COGS
- excludes inventory movements
- excludes cash/mobile-money debit lines

Avg Order Value:

- recognized revenue / completed order count
- ALL uses combined revenue / combined completed order count

Total Orders:

- online count uses completed online orders only
- walk-in count uses completed walk-in orders only
- ALL is the sum

Order Status:

- based on order tables, not journal entries
- based on creation date range, not payment date
- intended as operational distribution, not recognized revenue distribution

## Edge Cases

Handle explicitly:

- no data in current period
    - chart zero-filled
    - stats zeroed
    - status array either empty or zeroed stable labels
    - recommended: return only labels with non-zero values for the pie chart
- previous period has no data
    - do not divide by zero
- sparse data
    - preserve all time buckets
- walk-in-only period or online-only period under ALL
    - totals still work
- timezone
    - use server-local date boundaries consistently across current and previous windows
- refund entries
    - ignore for v1 because refund posting flow is not active in the current codebase

## Files Most Likely To Change

Primary:

- C:/Users/huvis/OneDrive/Desktop/Projects/Vestrex%20Commerce/perfume_budget/src/main/java/com/example/perfume_budget/controller/AdminMetricController.java
- C:/Users/huvis/OneDrive/Desktop/Projects/Vestrex%20Commerce/perfume_budget/src/main/java/com/example/perfume_budget/service/interfaces/AdminMetricService.java
- C:/Users/huvis/OneDrive/Desktop/Projects/Vestrex%20Commerce/perfume_budget/src/main/java/com/example/perfume_budget/service/AdminMetricServiceImpl.java

Also add:

- new DTO files under dto/admin_dashboard or a new dto/analytics package
- new enums under enums
- repository query additions in JournalEntryRepository, OrderRepository, and WalkInOrderRepository
- new projections under projection

## Test Plan

### Service Tests

Add tests for:

- source=ONLINE with only online journal sales
- source=WALK_IN with only walk-in journal sales
- source=ALL combining both
- each granularity:
    - day
    - week
    - month
    - year
- zero-fill bucket behavior
- mini-stat calculations
- trend calculations for:
    - increase
    - decrease
    - equal values
    - previous zero / current zero
    - previous zero / current positive
- order-status normalization for ALL

### Repository Tests

If repository tests exist for query-heavy code, add focused tests for:

- day/week/month/year revenue grouping
- journal line filtering excludes non-revenue lines
- type filtering excludes non-sale journal entries
- online and walk-in count queries respect date ranges

### Controller Tests

Add MVC tests for:

- default query param behavior
- valid explicit filters
- invalid date combinations
- enum parsing
- admin authorization enforcement

## Acceptance Criteria

The feature is complete when:

- one endpoint returns the full analytics payload
- the endpoint accepts source=ALL|ONLINE|WALK_IN
- the endpoint accepts granularity=DAY|WEEK|MONTH|YEAR
- ALL returns consolidated revenue and consolidated completed order totals
- online and walk-in revenue come from bookkeeping entries only
- total orders come from order tables, source-filtered correctly
- chart data is continuous and zero-filled
- summary trends compare against the immediately preceding equivalent period
- order-status data returns stable, frontend-usable labels
- automated tests cover source filters, granularities, trends, and empty states

## Assumptions

- Currency formatting remains Money(...).toString() and therefore follows current GHS formatting conventions.
- Revenue chart values should be raw numeric amounts, not preformatted strings.
- orderStatus is operational, so it is sourced from order tables rather than ledger entries.
- For ALL, walk-in completed orders remain a separate Completed slice rather than being merged into Delivered.
- v1 does not subtract refunds from revenue until an explicit refund-bookkeeping flow exists.