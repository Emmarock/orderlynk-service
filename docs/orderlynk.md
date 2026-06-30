1. Executive Summary
   Orderlynk is a commerce infrastructure platform designed to help small and growing vendors manage orders, payments, customers, and fulfillment across social channels and marketplaces. The first wedge is African and diaspora commerce, especially vendors in Canada who currently receive orders through WhatsApp and Instagram and struggle with manual tracking, payment confirmation, pickup coordination, and delivery visibility.
   The platform will begin as a vendor operating system and evolve into a marketplace plus fulfillment rail. It will support local same-city purchases, Canada city-to-city purchases, import batch orders from African countries into Canada, and future reverse cross-border commerce.
   Strategic Thesis
   Start with vendors who already sell informally through WhatsApp and Instagram. Give them structured order links, payment tracking, order dashboards, and fulfillment visibility. Once supply is onboarded, layer marketplace discovery and logistics partner integrations on top.

2. Product Vision and Mission
   Area
   Definition
   Vision
   To become the commerce rails that power local, domestic, and cross-border trade for community-based vendors and global customers.
   Mission
   Help vendors turn social demand into structured orders, secure payments, reliable fulfillment, and repeat customers.
   Initial Wedge
   African and diaspora vendors in Canada selling through WhatsApp, Instagram, Shopify, and physical stores.
   Long-Term Expansion
   Serve any vendor and customer globally where local delivery, payment integration, and fulfillment coordination are needed.

3. Core Business Definition
   Orderlynk is not only a marketplace. The marketplace is one visible layer, but the product is broader. It provides operational rails that support the complete transaction journey from discovery to delivery.
   Layer
   What Orderlynk Provides
   Discovery Rail
   Vendor storefronts, product pages, social links, marketplace search, verified vendor profiles.
   Order Rail
   Structured carts, order IDs, customer details, fulfillment selection, status tracking.
   Payment Rail
   Payment collection or tracking, platform fees, vendor payable amounts, logistics fees, payout reporting.
   Fulfillment Rail
   Local pickup, local delivery, domestic shipping, import batch tracking, pickup codes.
   Communication Rail
   WhatsApp, Instagram links, email/SMS notifications, reminders, order tracking links.
   Operations Rail
   Vendor dashboard, admin dashboard, dispute handling, payout reports, analytics.

4. Problem Statement
   Vendors receive orders through WhatsApp and Instagram DMs but lack structured order capture.
   Payment confirmations are scattered across screenshots, chats, and manual notes.
   Customers keep asking for updates because order and fulfillment status is not visible.
   Pickup and delivery are difficult to coordinate, especially for vendors serving multiple cities.
   Shopify vendors may have storefronts but still lack community discovery, social-channel order flow, and local/city-to-city fulfillment rails.
   Diaspora import vendors need batch management, payment tracking, shipment updates, and pickup coordination.
   Customers need a trusted place to discover verified vendors and track purchases from order to delivery.
5. Target Users and Personas
   Persona
   Description
   Primary Needs
   WhatsApp Vendor
   Seller using WhatsApp groups/status to receive orders manually.
   Structured order links, payment tracking, pickup list, customer database.
   Instagram Vendor
   Seller promoting products through posts, reels, stories, and DMs.
   Link-in-bio storefront, product pages, order conversion, campaign tracking.
   Shopify Vendor
   Seller with an existing store but limited diaspora/community distribution.
   Additional sales channel, marketplace discovery, fulfillment coordination.
   Physical Store
   African/diaspora retail store serving a local city.
   Online catalog, pickup orders, city-to-city reach, inventory visibility.
   Import Batch Vendor
   Vendor sourcing products from Nigeria/Africa into Canada.
   Batch windows, paid/unpaid tracking, shipment updates, pickup coordination.
   Customer
   Buyer shopping locally, city-to-city, or through import batches.
   Trust, simple checkout, payment confirmation, order tracking, pickup/delivery updates.
   Admin/Operations
   Internal team managing vendors, orders, fees, disputes, and rollout.
   Control, visibility, reporting, vendor approval, payout and logistics oversight.

6. Product Scope
   6.1 MVP Scope
   Vendor signup and approval.
   Vendor storefront with shareable WhatsApp and Instagram order links.
   Product catalog management.
   Customer order flow: browse, cart, checkout, confirmation.
   Payment collection or payment status tracking.
   Vendor order dashboard with paid/unpaid and fulfillment statuses.
   Customer order tracking page.
   Pickup/delivery status management.
   Admin dashboard for vendor approval and order oversight.
   Basic payout and platform fee reporting.
   6.2 Out of Scope for MVP
   Native iOS and Android apps.
   Full AI WhatsApp assistant.
   Full logistics partner portal.
   Automated customs/duties engine.
   Multi-country tax engine.
   Full Shopify two-way sync.
   Automated split payouts to every party on day one.
   Complex inventory forecasting.
7. Supported Commerce Flows
   Flow
   Example
   Fulfillment Logic
   MVP Priority
   Same-City Commerce
   Winnipeg customer buys from Winnipeg vendor.
   Pickup or same-city delivery.
   High
   Domestic City-to-City Commerce
   Winnipeg customer buys from Toronto vendor.
   Domestic shipping or courier tracking.
   High
   Import Batch Commerce
   Canada customer joins Nigeria-to-Canada batch.
   Batch window, sourcing, cargo, arrival, pickup.
   Medium
   Marketplace Discovery
   Customer finds a vendor outside WhatsApp.
   Vendor listing, search, checkout.
   Medium
   Reverse Cross-Border Commerce
   Nigeria customer buys from Canada vendor.
   Export flow, cross-border payment, delivery.
   Future

8. Customer Journey
   Customer discovers vendor through WhatsApp, Instagram, marketplace, QR code, or referral link.
   Customer clicks vendor storefront or product link.
   Customer browses vendor products without forced account creation.
   Customer adds products to cart and selects fulfillment option.
   Customer enters name, phone, email, city, and pickup/delivery/shipping preference.
   Customer pays or receives payment instructions depending on the MVP payment model.
   Orderlynk creates an Order ID and order tracking page.
   Vendor receives order in dashboard and prepares fulfillment.
   Customer receives status updates and pickup/delivery instructions.
   Order is marked completed; customer can reorder, rate, or share referral link.
   Customer Promise
   Order trusted products from verified vendors, pay securely, track every step, and receive items through pickup, delivery, domestic shipping, or import batch fulfillment without the chaos of manual chat ordering.

9. Vendor Journey
   Vendor applies to join Orderlynk.
   Admin reviews and approves vendor.
   Vendor creates storefront profile.
   Vendor uploads products and sets prices, availability, city, and fulfillment options.
   Platform generates shareable WhatsApp and Instagram order links.
   Vendor posts link in WhatsApp group/status, Instagram bio, stories, or DMs.
   Customer orders through Orderlynk.
   Vendor manages orders from dashboard instead of chat threads.
   Vendor updates payment and fulfillment status.
   Vendor receives payout or payment reconciliation report.
   Vendor reviews customer and sales analytics.
10. Key Features and Requirements
    Feature Area
    MVP Requirement
    Notes
    Vendor Onboarding
    Vendors submit business details; admin approves or rejects.
    Support assisted onboarding initially.
    Vendor Storefront
    Public page with vendor profile, products, location, fulfillment options, and contact links.
    Must be mobile friendly.
    Product Catalog
    Vendor can add/edit products, images, price, category, quantity, and fulfillment type.
    Support active/inactive product toggle.
    Order Link Generator
    Generate shareable URLs for WhatsApp/Instagram with source/campaign tracking.
    Example: ?source=whatsapp&campaign=june-batch.
    Cart & Checkout
    Customer adds products, selects fulfillment, enters details, sees full cost, and submits order.
    Browse before signup preferred.
    Payment Tracking
    Order has clear payment status: pending, paid, partial, refunded, cancelled.
    Stripe or manual e-transfer in MVP.
    Order Dashboard
    Vendor sees order list, paid/unpaid, customer details, items, total, status.
    Core vendor value.
    Fulfillment Tracking
    Vendor updates fulfillment status based on selected fulfillment type.
    Different status flows per fulfillment type.
    Customer Tracking
    Customer can track order by order ID and phone/email.
    Show payment and fulfillment status.
    Admin Console
    Admin manages vendors, orders, fees, statuses, disputes, and reports.
    Essential for pilot control.
    Notifications
    Send order confirmation, payment confirmation, status updates, pickup reminders.
    Email first; WhatsApp API later.
    Payout Reporting
    Calculate gross sales, platform fees, logistics fees, vendor payable, refunds.
    Can be manual payout initially.

11. Payment and Fee Management
    Payment design is central to the solution because it affects trust, vendor adoption, platform monetization, refunds, and operational control.
    Payment Model
    Description
    Recommendation
    Vendor Collects Directly
    Customer pays vendor directly; platform tracks payment status and invoices vendor for fees.
    Good for early pilot, but weak fee control.
    Platform Collects and Settles
    Customer pays platform; platform calculates vendor payable, logistics payable, platform fee, and settles manually or weekly.
    Recommended for improved MVP.
    Automated Marketplace Split
    Customer pays once; payment provider splits funds to vendor/logistics/platform.
    Best long-term model using Stripe Connect or equivalent.

Order Component
Description
Product Subtotal
Total value of products ordered from vendor.
Logistics Fee
Pickup hub, local delivery, domestic shipping, or import batch logistics fee.
Platform Fee
Service fee, order fee, vendor commission, or subscription-based fee.
Processing Fee
Payment provider fee where applicable.
Vendor Payable
Amount due to vendor after platform deductions/refunds.
Logistics Payable
Amount due to logistics partner where applicable.
Platform Revenue
Platform fee retained by Orderlynk.

MVP Recommendation
Use platform-managed checkout where possible. Record an internal ledger for every order even if vendor and logistics payouts are manual at first.

12. Fulfillment Types and Status Flows
    Fulfillment Type
    Status Flow
    Local Pickup
    Order Received -> Paid -> Vendor Confirmed -> Ready for Pickup -> Completed
    Local Delivery
    Order Received -> Paid -> Vendor Confirmed -> Preparing -> Out for Delivery -> Delivered -> Completed
    Domestic Shipping
    Order Received -> Paid -> Vendor Confirmed -> Packed -> Shipped -> Delivered -> Completed
    Import Batch
    Order Received -> Paid -> Assigned to Batch -> Sourcing -> Packed -> Shipped -> Arrived -> Ready for Pickup -> Completed
    Export Batch (Future)
    Order Received -> Paid -> Export Processing -> Shipped -> Arrived -> Delivered -> Completed

13. Bubble Implementation Guide
    The first improved MVP can be built in Bubble as a mobile-responsive web app. Bubble should be used for the database, workflows, dashboards, vendor storefronts, checkout, tracking, and admin screens. Custom code should be limited to small utilities such as order ID generation, QR code display, and formatted WhatsApp share links.
    13.1 Bubble Pages
    Page
    Purpose
    index
    Marketplace landing page and value proposition.
    vendor-store
    Public vendor storefront and product listings.
    product-detail
    Product details, fulfillment information, add-to-cart.
    cart
    Cart summary and cost breakdown.
    checkout
    Customer information, fulfillment selection, payment.
    order-confirmation
    Order success, order ID, next steps.
    track-order
    Customer tracking by Order ID and phone/email.
    vendor-dashboard
    Vendor KPIs and quick actions.
    vendor-products
    Vendor product management.
    vendor-orders
    Vendor order management and status updates.
    admin-dashboard
    Platform-level operational dashboard.
    admin-vendors
    Vendor review and approval.
    admin-orders
    All orders and issue management.

13.2 Bubble Data Types
Data Type
Key Fields
User
Full Name, Phone, Role, City, Country, Linked Vendor, Is Admin.
Vendor
Business Name, Owner, Description, City, Country, WhatsApp Number, Instagram Handle, Logo, Verification Status, Store Slug, Fulfillment Types, Active, Rating.
Product
Vendor, Name, Description, Category, Price, Currency, Quantity Available, Product Image, Fulfillment Type, Origin Country, Available Now, Batch, Active.
Order
Order ID, Customer, Customer Name, Customer Phone, Customer Email, Vendor, Order Items, Product Subtotal, Logistics Fee, Platform Fee, Processing Fee, Total Amount, Vendor Payable, Logistics Payable, Platform Revenue, Payment Status, Fulfillment Type, Fulfillment Status, Batch, Pickup Code, Source Channel, Notes.
Order Item
Order, Product, Product Name Snapshot, Quantity, Unit Price, Line Total, Vendor.
Batch
Batch Name, Route, Origin Country, Destination Country, Destination City, Open Date, Close Date, Estimated Arrival, Batch Status, Vendor.
Payment Record
Order, Customer, Vendor, Amount Paid, Payment Method, Payment Status, Transaction Reference, Stripe Payment ID, Paid Date, Refund Status.
Payout
Vendor, Period Start, Period End, Gross Sales, Platform Fees, Logistics Fees, Refunds, Net Payout, Payout Status, Paid Date.
Notification Log
User, Order, Channel, Template, Status, Sent Date.

13.3 Bubble Option Sets
Option Set
Values
User Role
Customer, Vendor, Admin, Logistics Partner.
Vendor Status
Draft, Submitted, Under Review, Approved, Rejected, Suspended.
Product Category
Groceries, Beauty, Fashion, Household, Electronics, Baby & Kids, Event Items, Other.
Fulfillment Type
Local Pickup, Local Delivery, Domestic Shipping, Import Batch, Export Batch.
Payment Status
Pending, Paid, Partial, Failed, Refunded, Cancelled.
Fulfillment Status
Order Received, Payment Pending, Paid, Vendor Confirmed, Preparing, Ready for Pickup, Packed, Shipped, Delivered, Completed, Cancelled.
Batch Status
Open, Closed, Sourcing, Consolidating, Shipped, Arrived, Ready for Pickup, Completed, Delayed.
Source Channel
WhatsApp, Instagram, Marketplace, Vendor Link, Manual.
Payment Method
Card, Interac E-transfer, Cash, Bank Transfer, Stripe, Other.

14. Core Workflows for Developer
    Workflow
    Trigger
    Expected Result
    Vendor Signup
    Vendor submits application form.
    Create Vendor record, set status Submitted, notify admin.
    Admin Vendor Approval
    Admin clicks Approve.
    Vendor status becomes Approved, storefront becomes active.
    Add Product
    Vendor submits product form.
    Create Product linked to Vendor.
    Generate Share Link
    Vendor opens share panel.
    Create WhatsApp/Instagram URL with source/campaign tracking.
    Customer Order
    Customer clicks Place Order.
    Create Order and Order Items, calculate fees, generate Order ID.
    Payment Success
    Payment provider confirms charge.
    Payment Status = Paid, create Payment Record, notify vendor/customer.
    Manual Payment Confirmation
    Vendor/admin marks order as paid.
    Payment Status = Paid and notification sent.
    Vendor Updates Fulfillment
    Vendor changes status.
    Order status updates; customer receives notification.
    Ready for Pickup
    Vendor selects Ready for Pickup.
    Generate pickup code and send pickup instructions.
    Track Order
    Customer enters Order ID + phone/email.
    Display order status, items, vendor, pickup/delivery details.
    Weekly Payout Report
    Scheduled backend workflow.
    Create payout summary for each vendor.

15. WhatsApp and Instagram Flow
    WhatsApp and Instagram are acquisition and communication channels. Orderlynk is the system of record.
    Vendor creates a product, storefront, or batch page in Orderlynk.
    Orderlynk generates a shareable URL with source tracking parameters.
    Vendor posts the link in WhatsApp group/status, Instagram bio, story, reel, or DM.
    Customer clicks the link and lands on the vendor storefront or product page.
    Customer orders through the platform rather than chat.
    Vendor sees structured orders in dashboard.
    Payment, fulfillment, and notifications are managed through Orderlynk.
    Example Link
    https://orderlynk.app/vendor/mama-t-foods?source=whatsapp&campaign=june-batch

16. Notification Requirements
    Notification
    Recipient
    Channel
    Trigger
    Order Received
    Customer
    Email / WhatsApp template
    Order created.
    New Order Alert
    Vendor
    Email / Dashboard alert
    New customer order created.
    Payment Confirmed
    Customer and Vendor
    Email / WhatsApp template
    Payment status becomes Paid.
    Payment Reminder
    Customer
    Email / WhatsApp template
    Payment pending after configured period.
    Ready for Pickup
    Customer
    Email / WhatsApp / SMS future
    Fulfillment status becomes Ready for Pickup.
    Order Shipped
    Customer
    Email / WhatsApp
    Fulfillment status becomes Shipped.
    Delivered / Completed
    Customer and Vendor
    Email / Dashboard alert
    Order completed.
    Batch Update
    Customer
    Email / WhatsApp
    Batch status changes.

17. Privacy, Access, and Security Requirements
    Customers can only view their own orders.
    Vendors can only view their own products, customers, and orders.
    Admins can view and manage all vendors, orders, payments, and disputes.
    Payment records should not expose unnecessary customer payment details.
    Every payment and fulfillment status change should be logged.
    Vendor application data should be private until vendor is approved.
    Use role-based access rules from day one.
    Avoid storing sensitive card data directly in the app; use payment provider tokens and references.
18. Success Metrics
    Metric Category
    Metrics
    Vendor Adoption
    Active vendors, vendor time-to-first-order, products listed, vendor retention.
    Customer Adoption
    First-order conversion, repeat purchase rate, referral rate, account creation rate.
    Transaction Volume
    Completed orders, gross transaction value, average order value, orders per vendor.
    Payment Health
    Payment success rate, pending payment rate, refund rate, reconciliation time.
    Fulfillment Health
    Pickup completion rate, delivery success rate, shipping delay rate, unclaimed orders.
    Platform Revenue
    Platform fees, commissions, subscription revenue, logistics margin.
    Support Load
    Where-is-my-order tickets, payment confirmation tickets, pickup confusion incidents.

19. MVP Release Plan
    Release
    Scope
    Success Criteria
    Release 1: Vendor Rails
    Vendor signup, approval, storefront, product catalog, order link, basic orders.
    5 pilot vendors can receive structured orders.
    Release 2: Checkout & Tracking
    Cart, checkout, order ID, payment tracking, customer tracking page.
    Customers can order and track without manual chat.
    Release 3: Vendor Dashboard
    Paid/unpaid, fulfillment statuses, pickup code, customer list.
    Vendors can manage orders better than WhatsApp.
    Release 4: Marketplace Beta
    Customer discovery by city/category/vendor, verified vendor badges.
    Customers discover vendors outside WhatsApp/Instagram.
    Release 5: Logistics & Batch
    Domestic shipping status, import batch statuses, pickup hub support.
    Platform supports local, domestic, and import batch orders.

20. Recommended Pilot Plan
    Start with 5 to 10 vendors in Winnipeg, Toronto, Calgary, or Edmonton.
    Prioritize vendors already receiving orders through WhatsApp or Instagram.
    Offer to digitize their next 50 orders for free or at a discounted pilot rate.
    Build each vendor storefront manually with 10 to 30 products.
    Ask vendors to post Orderlynk links in their existing WhatsApp groups and Instagram profiles.
    Measure order completion, payment completion, vendor satisfaction, and support requests.
    Convert successful vendors into paid plans or per-order fee agreements.
    Pilot Success Target
    5-10 vendors, 300-500 completed orders, 25-40% repeat customers, and at least 3 vendors willing to pay after pilot.

21. Monetization Model
    Revenue Stream
    Description
    When to Use
    Per-Order Fee
    Flat fee per completed order.
    Best for WhatsApp vendors and early pilots.
    Vendor Subscription
    Monthly fee for vendor tools and dashboard.
    Best once vendors rely on the system.
    Marketplace Commission
    Percentage fee on orders discovered through Orderlynk.
    Best when platform brings customer demand.
    Customer Service Fee
    Visible service fee at checkout.
    Useful for platform monetization but should be tested carefully.
    Logistics Margin
    Markup or coordination fee on delivery/shipping.
    Use when platform negotiates logistics rates.
    Featured Listings
    Paid placement for vendors/products.
    Later marketplace revenue stream.

22. Risks and Mitigation
    Risk
    Impact
    Mitigation
    Vendor resistance
    Vendors fear customer loss or extra fees.
    Position as vendor operating system; allow vendor-owned links and branding.
    Payment complexity
    Refunds and payouts become messy.
    Use internal ledger from day one; start with weekly settlements.
    Low customer trust
    Customers hesitate to pay through new platform.
    Use verified vendor badges, tracking, clear refund policy, and social proof.
    Operational overload
    Too many flows too early.
    Start with local and domestic orders before complex cross-border scale.
    Poor vendor fulfillment
    Customer experience suffers.
    Admin approval, vendor scorecards, pilot vetting, suspension rules.
    Manual support burden
    Scaling becomes expensive.
    Automate payment reminders, order confirmations, pickup alerts, and status updates.
    Regulatory/payment obligations
    Risk from holding and transferring funds.
    Use reputable payment provider and seek legal/tax guidance before scaling payouts.

23. Future Enhancements
    WhatsApp Business API assistant for ordering, tracking, reminders, and support.
    Instagram DM automation and shop-this-post links.
    Shopify integration for catalog sync and order import/export.
    Logistics partner portal for delivery assignment and status updates.
    Customer mobile app for reorder, tracking, loyalty, and notifications.
    Vendor analytics dashboard showing top products, repeat customers, and city demand.
    AI product suggestions and reorder reminders.
    Group ordering for families, churches, student groups, and events.
    Automated split payouts using marketplace payment infrastructure.
    Multi-country expansion with localized currencies, taxes, and fulfillment rules.
24. Implementation Guidance Summary
    Build Philosophy
    Build the vendor operating system first. Use it to onboard supply and capture real transactions. Then add marketplace discovery and logistics rails. Avoid overbuilding before validating vendor and customer adoption.

Set up Bubble database and option sets.
Build vendor signup, admin approval, and vendor storefront pages.
Build product catalog and public product listing pages.
Build cart, checkout, order creation, and fee calculation workflows.
Integrate Stripe or support manual payment tracking for MVP.
Build vendor order dashboard with status updates and pickup code generation.
Build customer tracking page.
Build admin dashboard and payout reporting.
Add email notifications and WhatsApp/Instagram share links.
Pilot with 5-10 real vendors before expanding.
25. Glossary
    Term
    Definition
    Vendor Operating System
    Tools that help vendors manage orders, products, payments, customers, and fulfillment.
    Commerce Rails
    The underlying infrastructure that supports ordering, payment, tracking, and fulfillment.
    Source Channel
    The channel where the customer came from, such as WhatsApp, Instagram, marketplace, or vendor link.
    Fulfillment Type
    The method used to complete the order: pickup, delivery, domestic shipping, import batch, or export batch.
    Import Batch
    A grouped shipment from a source country to a destination country with defined order and arrival windows.
    Vendor Payable
    Amount due to the vendor after platform deductions, logistics fees, and refunds.
    Platform Fee
    Revenue retained by Orderlynk for providing the transaction and fulfillment rails.

Appendix A: Useful Logic Snippets
Order ID Format
Recommended format: OB-YYMMDD-RANDOM, for example OB-260601-4821. This is readable and easier for customers to reference than internal database IDs.
Fee Calculation Logic
Total Amount = Product Subtotal + Logistics Fee + Platform Fee + Processing Fee
Vendor Payable = Product Subtotal - Vendor Commission/Adjustments - Refunds
Platform Revenue = Platform Fee + Commission + Logistics Margin, where applicable
Share Link Format
https://orderlynk.app/vendor/{vendor-slug}?source={channel}&campaign={campaign-name}
