package org.hopebridge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api")
public class DonationController {
    private final JdbcTemplate db;
    private final RestClient http = RestClient.create();
    @Value("${flutterwave.secret-key}") private String secretKey;
    @Value("${flutterwave.webhook-secret}") private String webhookSecret;
    @Value("${app.currency}") private String currency;
    @Value("${app.frontend-url}") private String appUrl;

    private static final Map<String, Item> CATALOG = Map.of(
      "school-kit", new Item("school-kit", "Books & stationery", "Exercise books, pens and essential classroom supplies for a learner.", 25000),
      "food-pack", new Item("food-pack", "School fees support", "Contribute towards school fees and help keep a student learning.", 50000),
      "health-kit", new Item("health-kit", "Digital learning kit", "Support access to basic digital learning tools and resources.", 75000)
    );

    public DonationController(JdbcTemplate db) { this.db = db; }

    record Item(String id, String name, String description, long price) {}
    record CartItem(@NotBlank String id, @Min(1) @Max(10) int quantity) {}
    record CheckoutRequest(@NotBlank @Size(max=120) String name,
                           @NotBlank @Email @Size(max=180) String email,
                           @Size(max=40) String phone,
                           @NotEmpty @Size(max=3) List<@Valid CartItem> items) {}
    record CheckoutResponse(String checkoutUrl, String reference) {}

    @GetMapping("/items")
    public Collection<Item> items() { return CATALOG.values(); }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest req) {
        if (secretKey == null || secretKey.isBlank())
            throw new IllegalStateException("Payments are not configured. Add FLW_SECRET_KEY on the server.");
        Set<String> unique = new HashSet<>();
        long total = 0;
        List<Map<String,Object>> receiptItems = new ArrayList<>();
        for (CartItem ci : req.items()) {
            if (!unique.add(ci.id())) throw new IllegalArgumentException("Each item can appear only once; change its quantity.");
            Item item = CATALOG.get(ci.id());
            if (item == null) throw new IllegalArgumentException("Unknown donation item.");
            total = Math.addExact(total, Math.multiplyExact(item.price(), ci.quantity()));
            receiptItems.add(Map.of("id", item.id(), "name", item.name(), "quantity", ci.quantity(), "unitPrice", item.price()));
        }
        if (total < 1) throw new IllegalArgumentException("Donation total must be positive.");
        String ref = "HB-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000,9999);
        String itemsJson = org.springframework.util.StringUtils.collectionToDelimitedString(
            receiptItems.stream().map(Object::toString).toList(), " | ");

        db.update("INSERT INTO donations(tx_ref,donor_name,donor_email,donor_phone,amount,currency,items_json,status) VALUES(?,?,?,?,?,?,?,'PENDING')",
            ref, req.name(), req.email(), req.phone(), BigDecimal.valueOf(total), currency, itemsJson);

        Map<String,Object> customer = new HashMap<>();
        customer.put("email", req.email()); customer.put("name", req.name());
        if (req.phone() != null && !req.phone().isBlank()) customer.put("phonenumber", req.phone());

        Map<String,Object> payload = new HashMap<>();
        payload.put("tx_ref", ref); payload.put("amount", total); payload.put("currency", currency);
        payload.put("redirect_url", appUrl + "/payment-result.html");
        payload.put("customer", customer);
        payload.put("customizations", Map.of("title", "HopeBridge Donations", "description", "Thank you for supporting a community"));
        try {
            Map response = http.post().uri("https://api.flutterwave.com/v3/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(Map.class);
            Map data = (Map) response.get("data");
            return new CheckoutResponse((String)data.get("link"), ref);
        } catch (RestClientResponseException ex) {
            db.update("UPDATE donations SET status='INITIATION_FAILED' WHERE tx_ref=?", ref);
            throw new IllegalStateException("Payment provider could not start checkout. Please try again.");
        }
    }

    @GetMapping("/verify")
    public Map<String,Object> verify(@RequestParam String transaction_id, @RequestParam String tx_ref) {
        return verifyAndMark(transaction_id, tx_ref);
    }

    private Map<String,Object> verifyAndMark(String transactionId, String txRef) {
        if (secretKey == null || secretKey.isBlank()) throw new IllegalStateException("Payments are not configured.");
        Map<String,Object> order = db.queryForMap("SELECT * FROM donations WHERE tx_ref=?", txRef);
        Map result = http.get().uri("https://api.flutterwave.com/v3/transactions/{id}/verify", transactionId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey).retrieve().body(Map.class);
        Map data = (Map) result.get("data");
        boolean valid = "successful".equals(data.get("status"))
            && txRef.equals(data.get("tx_ref"))
            && currency.equals(data.get("currency"))
            && new BigDecimal(String.valueOf(data.get("amount"))).compareTo((BigDecimal)order.get("AMOUNT")) >= 0;
        if (valid) {
            db.update("UPDATE donations SET status='SUCCESS', gateway_transaction_id=? WHERE tx_ref=? AND status<>'SUCCESS'",
                transactionId, txRef);
            return Map.of("status","SUCCESS","message","Thank you. Your donation has been confirmed.","reference",txRef);
        }
        db.update("UPDATE donations SET status='FAILED' WHERE tx_ref=? AND status='PENDING'", txRef);
        return Map.of("status","NOT_CONFIRMED","message","We could not confirm this payment. Contact support with your reference.","reference",txRef);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value="verif-hash", required=false) String signature,
                                        @RequestBody Map<String,Object> body) {
        if (webhookSecret == null || webhookSecret.isBlank() || signature == null
            || !java.security.MessageDigest.isEqual(webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                                    signature.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Object event = body.get("event");
        if ("charge.completed".equals(event)) {
            Object d = body.get("data");
            if (d instanceof Map<?,?> data && data.get("id") != null && data.get("tx_ref") != null) {
                try {
                    verifyAndMark(String.valueOf(data.get("id")), String.valueOf(data.get("tx_ref")));
                } catch (Exception ignored) {
                    // Return a failure so the provider can retry; do not mark a donation paid from webhook payload alone.
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/health")
    public Map<String,String> health() { return Map.of("status","ok"); }
}
