OrderLynk
Batch & Cargo Feature Product Document
Feature scope, user flows, data model, business rules, UI requirements, and implementation guidance for adding Batch Cycles and Customer Shipment Requests to the current OrderLynk platform.

Item
Details
Product
OrderLynk
Feature
Batch & Cargo Module
Primary users
Vendors, cargo/logistics partners, customers, platform admin
Initial route example
Nigeria to Canada via air or sea cargo
Primary goal
Professionalize batch orders and customer-owned cargo requests from social channels into structured orders, payments, shipment updates, and fulfillment tracking
Document purpose
Guide developer implementation with minimal ambiguity



1. Executive Summary
   OrderLynk currently helps vendors convert messy social-channel requests into structured orders, payments, customer records, and fulfillment workflows. The new Batch & Cargo feature extends this capability for vendors and cargo partners who run shipment cycles, import batches, pre-orders, and customer-owned item shipments across countries.
   The feature should support two related workflows: vendor-led batch selling and customer-supplied shipment requests. A vendor-led batch allows a vendor to create a cycle such as “January 1–31 Nigeria to Canada Batch,” attach products to that batch, collect customer orders, track payments, manage shipment progress, and update customers until pickup or delivery. A customer-supplied shipment request allows a customer to add their own items to a cargo batch, where the cargo partner weighs the items, charges per kilogram, collects payment, and fulfills the shipment.
   This should be implemented as a module within OrderLynk, not as a separate application, because it uses the same platform foundations: vendor storefronts, customer accounts, order records, payment tracking, fulfillment status, notifications, finance, and customer support.
2. Product Rationale
   Many vendors and cargo operators already manage batch-based import/export activities through WhatsApp, Instagram, phone calls, spreadsheets, and payment screenshots. This creates operational pain as order volume grows.
   Customers place requests across multiple chats and messages.
   Vendors struggle to track which customer belongs to which batch.
   Payment confirmation is often manual and screenshot-based.
   Customers repeatedly ask for updates during the batch cycle.
   Cargo companies charge by weight, but actual weight is often unknown until item drop-off.
   Pickup and delivery after arrival are often handled manually.
   Vendors lack batch-level revenue, item, customer, and fulfillment visibility.
   Customer-owned cargo requests are mixed with product orders, creating confusion.
   The new feature should help OrderLynk become the operating system for batch commerce and cargo-based fulfillment.
3. How This Fits the Current OrderLynk Platform
   Current OrderLynk Capability
   Batch & Cargo Extension
   Vendor storefront
   Vendor can display active batch cycles and batch products.
   Product catalog
   Products can be reused across batches without recreating them each month.
   Order management
   Orders can be tagged as Regular Order, Batch Product Order, or Shipment Request.
   Payment tracking
   Supports product payments, per-kg cargo charges, handling fees, and balance adjustments.
   Delivery / pickup tracking
   Extends fulfillment to include shipment cycle stages such as sourcing, cargo received, shipped, arrived, ready for pickup, delivered.
   Customer portal
   Customers can track their own batch orders and shipment requests.
   Vendor dashboard
   Vendors can manage batch cycles, batch products, customer orders, shipment requests, finance, and customer updates.
   Marketplace
   Batch campaigns can be discoverable as a distinct marketplace item type, clearly labeled as pre-order/batch/cargo service.


4. Feature Scope
   4.1 In Scope for MVP
   Vendor can create and manage a batch cycle.
   Vendor can add existing products from their product catalog to a batch.
   Vendor can copy products from a previous batch into a new batch.
   Vendor can override batch-specific price, quantity, availability, and notes.
   Customer can view an active batch page and purchase products into the batch.
   Customer can submit “Send My Items” / shipment request into a batch.
   Cargo/vendor can update estimated and actual weight for customer-owned shipment requests.
   System calculates per-kg charges, handling fee, total payable, and payment status.
   Vendor/cargo partner can update batch status and individual order/shipment status.
   Customers can view only their own order or shipment status.
   Vendor can see all batch orders and shipment requests for their business.
   Admin can view and manage all batches, orders, shipment requests, statuses, and exceptions.
   4.2 Out of Scope for MVP
   Automated cargo carrier API integration.
   Customs declaration automation.
   Automated insurance claims.
   Automated split payout to cargo partners and vendors.
   Multi-leg international tracking integrations.
   Complex volumetric weight pricing rules beyond simple actual-weight calculation.
   Customer-to-customer marketplace for cargo sharing.
5. User Personas and Roles
   Persona / Role
   Responsibilities / Needs
   Vendor
   Creates batch cycles, adds products, collects product orders, tracks payments, updates fulfillment, communicates with customers.
   Cargo partner
   Creates or manages cargo batches, receives customer-owned items, records actual weight, issues invoice, updates shipment status.
   Customer buying products
   Orders products into a vendor batch and tracks payment, batch progress, and pickup/delivery.
   Customer sending own items
   Submits shipment request, drops item at collection point, receives weight-based invoice, pays, tracks shipment.
   Admin
   Oversees vendors, cargo partners, batches, statuses, disputes, payments, and support issues.
   Staff / sub-user
   Vendor or cargo staff supporting order fulfillment, product management, collection point processing, or customer service.


6. Core Concepts
   Concept
   Definition
   Product Catalog
   Reusable product library created once by the vendor. Products are not recreated every batch.
   Batch Cycle
   A defined order/shipment window with origin, destination, open/close dates, shipping method, and estimated arrival.
   Batch Product
   A product from the vendor catalog attached to a specific batch with batch-specific price, quantity, and availability.
   Batch Product Order
   Customer purchases one or more batch products into a specific batch cycle.
   Shipment Request
   Customer submits their own item(s) to be added to a batch for cargo shipment, usually charged per kg.
   Batch Status
   Overall status of the batch shipment/cycle.
   Order/Shipment Status
   Status of an individual customer’s order or shipment request.
   Collection Point
   Origin location where customers or agents drop off items before shipment.
   Pickup/Delivery Point
   Destination location or delivery address after the batch arrives.


7. User Flows
   7.1 Vendor-Led Batch Product Flow
   Vendor logs into vendor portal.
   Vendor creates a new Batch Cycle, e.g., “January Foodstuff Batch.”
   Vendor enters origin, destination, open/close dates, shipping method, estimated arrival, and pickup details.
   Vendor adds products from existing product catalog or copies products from a previous batch.
   Vendor updates batch-specific price, quantity limit, minimum quantity, and product notes.
   Vendor publishes the batch.
   Vendor shares batch link on WhatsApp, Instagram, Facebook, or other channels.
   Customer opens batch page, selects products, chooses pickup/delivery option, and places order.
   Order is created with Batch ID and Order Type = Batch Product Order.
   Vendor tracks payment, product quantities sold, and customer fulfillment status.
   Vendor updates batch status as it moves through sourcing, packing, cargo, shipment, arrival, and pickup/delivery.
   Customer receives updates and tracks their own order until fulfilled.
   7.2 Customer-Supplied Shipment Request Flow
   Vendor/cargo partner creates an active Batch Cycle.
   Customer opens batch page and selects “Send My Items in This Batch.”
   Customer submits item description, number of packages, estimated weight, declared value, origin drop-off location, destination city, pickup/delivery preference, and contact details.
   System creates Shipment Request ID and initial status = Request Created / Awaiting Drop-Off.
   Customer or agent drops item at collection point.
   Cargo/vendor staff confirms receipt and records actual weight.
   System calculates final amount using actual weight x rate per kg + handling/delivery fees.
   Customer receives invoice/payment request.
   After payment, shipment request status changes to Paid / Added to Batch.
   Cargo/vendor updates shipment statuses until arrival.
   Customer receives pickup/delivery details and tracks request until completed.
   7.3 Hybrid Flow
   A customer may buy products listed by the vendor and also submit personal items into the same batch. The system should allow both to be visible in the customer portal as separate records under the same batch, or as a combined batch summary with clear separation between product order and shipment request.
8. Status Model
   8.1 Batch Statuses
   Status
   Meaning
   Draft
   Batch created but not visible to customers.
   Open for Orders
   Customers can buy products or submit shipment requests.
   Closing Soon
   Optional status or badge near order close date.
   Closed
   No new orders or shipment requests accepted.
   Sourcing / Receiving
   Vendor is sourcing products or cargo partner is receiving customer items.
   Packed / Consolidating
   Products/items are being packed or consolidated for cargo.
   At Cargo Partner
   Shipment has been handed to cargo partner or collection warehouse.
   Shipped
   Batch has departed origin country.
   Arrived Destination
   Batch has arrived in destination country/city.
   Cleared / Received
   Shipment has cleared or been received by destination handler.
   Ready for Pickup / Delivery
   Orders/items are ready for customer pickup or local delivery.
   Completed
   Batch cycle is fully fulfilled.
   Delayed
   Batch has a delay; customers should be notified.


8.2 Batch Product Order Statuses
Order Received
Payment Pending
Paid
Assigned to Batch
Sourcing
Packed
Shipped
Arrived
Ready for Pickup
Out for Delivery
Delivered / Collected
Completed
Cancelled / Refunded
8.3 Customer Shipment Request Statuses
Request Created
Awaiting Drop-Off
Received at Collection Point
Weighed
Invoice Generated
Payment Pending
Paid
Added to Batch
Shipped
Arrived
Ready for Pickup
Out for Delivery
Delivered / Collected
Completed
Cancelled
9. UI / Screen Requirements
   Area
   Required Changes
   Homepage
   Add Batch Orders / Batch & Cargo as a feature pillar. Explain regular orders, pre-orders, import batches, and customer shipment requests.
   Marketplace
   Show batch campaigns as a distinct item type, clearly labeled with order close date, route, estimated arrival, and call-to-action.
   Vendor Navigation
   Add Batch Cycles and Shipment Requests to vendor portal sidebar/menu.
   Batch Cycles Page
   List all batches with status, order count, shipment request count, revenue, pending payments, close date, and estimated arrival.
   Create Batch Page
   Form for batch name, route, dates, shipping method, rate per kg, pickup details, collection points, notes, and visibility.
   Batch Products Page
   Select products from catalog, copy from previous batch, override price/quantity/status, publish or hide products.
   Batch Detail Dashboard
   Show total orders, paid/unpaid, shipment requests, total kg, revenue, batch status, customer updates, and filters.
   Customer Batch Page
   Show batch details and two primary options: Shop Batch Products and Send My Items in This Batch.
   Shipment Request Form
   Capture item description, package count, estimated weight, declared value, drop-off contact, destination, pickup/delivery preference, and notes.
   Customer Portal
   Show only customer’s own product orders and shipment requests with timeline and payment status.
   Admin Dashboard
   Manage all batches, shipment requests, exceptions, vendors/cargo partners, and manual status changes.


10. Marketplace Behavior
    Batch cycles and batch products may appear in the general marketplace, but they must be visually distinct from products available now. The marketplace should avoid customer confusion by using clear badges and filters.
    Show “Available Now” badge for regular local inventory.
    Show “Batch Order” or “Pre-Order Batch” badge for batch products.
    Show “Send Items” or “Cargo Batch” badge for customer shipment service cards.
    Expose filters: Available Now, Batch Orders, Send My Items, Local Pickup, Local Delivery, Domestic Shipping, Import Batch, Origin Country, Destination City.
    Batch product cards must show batch name, order close date, estimated arrival, origin, destination, and vendor/cargo partner.
    Customer should not be able to assume a batch item is available for immediate pickup unless the status says so.
11. Data Model / Database Changes
    11.1 Batch
    Field
    Type / Notes
    Batch ID
    Unique identifier
    Vendor ID
    Owner vendor or cargo partner
    Batch Name
    Text, e.g., January Nigeria to Canada Batch
    Batch Type
    Product Batch, Cargo Batch, Hybrid Batch
    Origin Country
    Text or lookup
    Origin City
    Text or lookup
    Destination Country
    Text or lookup
    Destination City/Cities
    List
    Shipping Method
    Air Cargo, Sea Cargo, Domestic, Other
    Order Open Date
    Date/time
    Order Close Date
    Date/time
    Estimated Departure Date
    Date/time
    Estimated Arrival Date
    Date/time
    Rate Per Kg
    Number; optional for cargo/shipment requests
    Handling Fee
    Number; optional
    Pickup Location
    Text or related location object
    Collection Point(s)
    List of collection point records
    Batch Status
    Option set
    Public Visibility
    Draft, Private Link, Marketplace Visible
    Notes
    Text


11.2 Batch Product
Field
Type / Notes
Batch Product ID
Unique identifier
Batch ID
Related batch
Product ID
Related master product
Vendor ID
Owner vendor
Batch Price
Number; can override default price
Quantity Limit
Number
Sold Quantity
Calculated
Minimum Order Quantity
Number; optional
Status
Available, Sold Out, Hidden
Batch-Specific Notes
Text
Estimated Availability
Date or text


11.3 Shipment Request
Field
Type / Notes
Shipment Request ID
Unique identifier
Customer ID
Related customer
Batch ID
Related batch
Vendor/Cargo Partner ID
Related vendor/cargo partner
Item Description
Text
Number of Packages
Number
Estimated Weight
Number
Actual Weight
Number; entered after receipt/weighing
Rate Per Kg
Number; default from batch but editable with permission
Handling Fee
Number
Delivery Fee
Number; optional destination delivery fee
Total Charge
Calculated
Payment Status
Pending, Paid, Partial, Refunded, Cancelled
Shipment Status
Option set
Origin Drop-Off Location
Text or location object
Destination Pickup/Delivery Location
Text/address
Declared Value
Number/currency
Restricted Item Confirmation
Boolean
Photos / Attachments
List of files/images; optional
Notes
Text


12. Business Rules
    A vendor should not recreate the same products for every batch. Products should be created once in the master catalog and reused or copied into batches.
    A batch can contain batch products, shipment requests, or both.
    Customers can only see their own orders and shipment requests in their portal.
    Vendors can see all orders and shipment requests associated with their business.
    Admin can see all records across the platform.
    Batch products can have batch-specific price and quantity overrides.
    Shipment request payment should preferably be finalized after actual weight is entered.
    If actual weight differs from estimated weight, the system should calculate the final amount based on actual weight.
    A batch cannot accept new orders after close date unless the vendor/admin reopens it.
    Batch status updates should optionally trigger customer notifications.
    Order status and batch status are separate. Batch status describes the overall cycle; order/shipment status describes the individual customer item.
13. Pricing, Fees, and Finance
    13.1 Product Batch Orders
    Product subtotal = sum of batch product line items.
    Platform fee may be fixed, percentage-based, or vendor subscription-based.
    Delivery/pickup fee may be added if applicable.
    Vendor payable should be tracked separately from platform revenue and logistics fees.
    13.2 Customer Shipment Requests
    Initial request may use estimated weight for estimate only.
    Final charge should be calculated after actual weight is entered by vendor/cargo staff.
    Formula: Total Charge = (Actual Weight x Rate Per Kg) + Handling Fee + Delivery Fee + Other Charges.
    System should support invoice generation or payment request after weighing.
    Financial ledger should track cargo partner payable, vendor/platform fee, processing fees, refunds, and adjustments.
14. Notifications
    Notifications may be email, SMS, WhatsApp template, or in-app depending on current platform capability. MVP can use email/manual WhatsApp templates, but event triggers should be designed now.
    Trigger
    Recipient
    Message Purpose
    Batch published
    Customers / social link visitors
    Announce active batch and order deadline.
    Order created
    Customer, vendor
    Confirm order was received and linked to batch.
    Payment pending
    Customer
    Request payment or remind customer to pay.
    Payment confirmed
    Customer, vendor
    Confirm order or shipment is paid.
    Shipment request received
    Customer, cargo/vendor
    Confirm shipment request and provide drop-off instructions.
    Item received at collection point
    Customer
    Confirm cargo/vendor received item.
    Actual weight entered
    Customer
    Share final invoice amount.
    Batch status changed
    All affected customers
    Update sourcing/shipped/arrived/ready status.
    Ready for pickup/delivery
    Customer
    Share pickup code, location, delivery instructions.
    Completed
    Customer, vendor
    Confirm completion and invite reorder/feedback.


15. Permissions and Privacy
    Role
    Access
    Customer
    Can view and manage only their own batch orders and shipment requests.
    Vendor Owner
    Can create batches, products, shipment requests, update statuses, manage finance, customers, staff, and settings.
    Vendor Staff
    Can manage orders, products, shipment requests, and fulfillment based on assigned permissions. Finance access should be optional/restricted.
    Cargo Partner
    Can manage cargo batches, shipment requests, weight, invoices, and shipment statuses.
    Admin
    Full access to all records, statuses, vendors, cargo partners, finance, and support exceptions.


16. Acceptance Criteria
    Vendor can create a batch cycle with required route, date, and fulfillment fields.
    Vendor can attach existing products to a batch without recreating product records.
    Vendor can copy products from a previous batch.
    Customer can purchase batch products from the batch page.
    Customer can submit a shipment request to add their own items to a batch.
    Shipment request can move from Request Created to Awaiting Drop-Off to Received to Weighed to Invoice Generated to Paid to Shipped to Completed.
    Actual weight entry recalculates final customer charge.
    Vendor can see batch dashboard showing orders, shipment requests, paid/unpaid, total revenue, and batch status.
    Customer portal shows customer’s own order/shipment timeline only.
    Marketplace shows batch campaigns distinctly with labels and estimated dates.
    Admin can manage all batches and manually correct statuses or records.
    Status updates can trigger notification events or templates.
17. Recommended Implementation Roadmap
    Phase
    Implementation Focus
    Phase 1: Batch foundation
    Create Batch, Batch Product, attach/copy products, batch page, batch order creation, vendor batch dashboard.
    Phase 2: Shipment requests
    Add Send My Items flow, shipment request form, weight capture, invoice/payment status, shipment request status timeline.
    Phase 3: Customer tracking
    Customer portal timeline for batch product orders and shipment requests; pickup/delivery instructions.
    Phase 4: Marketplace exposure
    Show active batches in marketplace with filters, badges, and batch-specific CTAs.
    Phase 5: Notifications and automation
    Automated email/WhatsApp/SMS templates for batch/order/shipment status changes.
    Phase 6: Cargo/logistics integration
    Add cargo partner integrations, documents, tracking references, and API integration where available.


18. Suggested User-Facing Labels and Copy
    Context
    Recommended Label / Copy
    Feature name
    Batch & Cargo
    Vendor menu item
    Batch Cycles
    Cargo menu item
    Shipment Requests
    Customer CTA
    Send My Items in This Batch
    Customer CTA
    Shop This Batch
    Marketplace badge
    Batch Order
    Marketplace badge
    Cargo Batch
    Batch page copy
    This batch is open for orders and shipment requests until [date]. Estimated arrival: [date range].
    Weight invoice copy
    Your items have been received and weighed. Please review the final charge and complete payment to add them to this batch.
    Tracking copy
    Your item is part of [Batch Name]. Current status: [Status].


19. Open Questions for Product/Business Decision
    Will cargo partners be onboarded as a separate account type or as vendors with cargo capability?
    Should customers pay a deposit before item drop-off or only after actual weight is confirmed?
    Should shipment request items require photo upload at submission or at collection point?
    What restricted/prohibited item checklist should be displayed for Nigeria to Canada shipments?
    Should shipment requests support insurance/declared value fees in MVP?
    Should cargo rate per kg vary by destination city, shipping method, or item category?
    Should vendor/cargo partner be able to reopen a closed batch?
    What notification channel is mandatory for MVP: email, WhatsApp, SMS, or in-app?
20. Final Recommendation
    Implement Batch & Cargo as a core module inside OrderLynk. This should not be a separate application because it extends the same operating system: social request intake, structured orders, payment tracking, customer management, batch status, delivery/pickup coordination, and fulfillment updates. The feature should be visible as a major capability for vendors and cargo partners while keeping the customer experience simple: Shop Batch Products or Send My Items in This Batch.
