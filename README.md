# Zahara Fund — Education Donation Platform

Responsive education-focused donation storefront using HTML, CSS, JavaScript, Java 17, Spring Boot and Flutterwave hosted checkout.

## Included

- Zahara Fund branding and education fundraising messaging.
- Three educational giving packages:
  - Books & stationery — UGX 25,000
  - School fees support — UGX 50,000
  - Digital learning kit — UGX 75,000
- Select one, two or all three package types; adjust quantity.
- Live cart totals and mobile responsive layout.
- Backend validation and server-side price calculation.
- Flutterwave hosted checkout link creation and transaction verification.
- Webhook endpoint with secret-hash validation.
- H2 for local development and PostgreSQL JDBC driver for production.
- Dockerfile and Render Blueprint for deployment.

The prices and package descriptions are illustrative. Zahara Fund must confirm actual supported use of funds, procurement costs and beneficiary process before public fundraising.

## Run locally

Requirements: Java 17+, Maven 3.9+, Flutterwave test account.

```bash
export FLW_SECRET_KEY="FLWSECK_TEST-your-test-secret"
export FLW_WEBHOOK_SECRET="a-long-random-webhook-secret"
export APP_BASE_URL="http://localhost:8080"
export PAYMENT_CURRENCY="UGX"
mvn spring-boot:run
```

Open `http://localhost:8080`. Without a test secret, the storefront works but checkout will not initiate.

## Flutterwave configuration

This integration uses Flutterwave Standard hosted checkout. Your server creates a payment with a unique transaction reference, amount, currency, customer details and redirect URL. Flutterwave returns a hosted checkout link. The browser is redirected to that page; card/mobile-money details are not collected by Zahara Fund.

1. Open a Flutterwave merchant account and complete onboarding/KYC.
2. Use test credentials first; set `FLW_SECRET_KEY` only in server environment variables.
3. Configure webhook URL: `https://YOUR-DOMAIN/api/webhook`.
4. Set the same webhook secret hash in the dashboard and `FLW_WEBHOOK_SECRET`.
5. Set `APP_BASE_URL=https://YOUR-DOMAIN`.
6. Test card and Uganda mobile-money flows, pending states, cancellations, failed payments and duplicate notifications.
7. Verify settlement, fees, supported UGX payment methods and payout terms directly with Flutterwave before enabling live payments.

Official docs:
- Hosted checkout: https://developer.flutterwave.com/docs/collections-inflow/flutterwave-standard
- Webhooks: https://developer.flutterwave.com/docs/webhooks
- API checkout reference: https://developer.flutterwave.com/api/charge/standard

Do not rely on a browser redirect alone as proof of payment. Always verify transaction ID, transaction reference, status, currency and amount on the server before recording a successful donation or allocating funds.

## Production database

For production, create a managed PostgreSQL database and configure:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:5432/DATABASE?sslmode=require`
- `DATABASE_USER=...`
- `DATABASE_PASSWORD=...`

Use a managed database with automated backups, restricted network access and TLS. The default H2 file database is for local development only. Add Flyway/Liquibase migrations and test the schema against PostgreSQL before launch.

## Deploy on Render

1. Push this project to a private GitHub repository.
2. Create a PostgreSQL database in Render.
3. Create a Web Service from the repository; select Docker (Render uses the included `Dockerfile`).
4. Add the environment variables above, using the internal database connection string converted to JDBC format if needed.
5. Set `APP_BASE_URL` to the deployed HTTPS URL.
6. Set the webhook URL in Flutterwave to `https://YOUR-DOMAIN/api/webhook`.
7. Deploy and check `https://YOUR-DOMAIN/api/health`.
8. Run full sandbox payments before switching to live credentials.

Render's Docker deployment documentation: https://render.com/docs/docker

## Pre-launch checklist

- Confirm Zahara Fund's legal identity, registration/authority to solicit donations and account ownership.
- Replace illustrative copy, amounts and impact claims with accurate approved information.
- Publish privacy notice, terms, refund/cancellation policy, contact information and explanation of how donations are used.
- Confirm Flutterwave KYC, UGX and Uganda payment method enablement, fees, settlement and payout terms.
- Configure a public HTTPS domain and webhook.
- Test successful, failed, cancelled, delayed/pending and duplicate payment cases.
- Use production PostgreSQL with backups; do not use ephemeral local storage.
- Add rate limiting, security headers, request size limits, structured logs, monitoring, database migrations and admin authentication.
- Add admin reconciliation and donor receipt emails before treating this as a complete fundraising operation.
- Ensure webhook processing is idempotent and performs server-side provider verification before marking donations successful.

## Current scope / limitations

This is a deployable starter project, not a live registered charity account or a fully audited production donation system. The included webhook validates Flutterwave's configured secret hash and records the transaction identifier for a pending donation; the transaction verification endpoint remains the authoritative status check. Before production, complete the asynchronous webhook verification path and idempotent processing, test with Flutterwave, and add donor receipt and administration workflows.
