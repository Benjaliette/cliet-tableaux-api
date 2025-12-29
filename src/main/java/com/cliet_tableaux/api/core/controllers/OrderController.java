package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.CheckoutSessionRequest;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionResponse;
import com.cliet_tableaux.api.core.services.OrderService;
import com.cliet_tableaux.api.core.services.WebhookService;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;
    private final WebhookService webhookService;

    public OrderController(OrderService orderService, WebhookService webhookService) {
        this.orderService = orderService;
        this.webhookService = webhookService;
    }

    @PostMapping("/create-checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createOrder(@RequestBody CheckoutSessionRequest checkoutSessionRequest) throws StripeException {
        return new ResponseEntity<>(orderService.createCheckoutSession(checkoutSessionRequest), HttpStatus.CREATED);
    }

    @PostMapping("/stripe-webhooks")
    public ResponseEntity<Void> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            webhookService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
