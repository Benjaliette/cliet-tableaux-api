package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.CheckoutSessionRequest;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionResponse;
import com.cliet_tableaux.api.core.dtos.OrderSummaryDto;
import com.cliet_tableaux.api.core.model.User;
import com.cliet_tableaux.api.core.services.OrderService;
import com.cliet_tableaux.api.core.services.WebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final WebhookService webhookService;

    public OrderController(OrderService orderService, WebhookService webhookService) {
        this.orderService = orderService;
        this.webhookService = webhookService;
    }

    @PostMapping("/create-checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createOrder(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CheckoutSessionRequest checkoutSessionRequest) throws StripeException {
        return new ResponseEntity<>(orderService.createCheckoutSession(currentUser, checkoutSessionRequest), HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<List<OrderSummaryDto>> getMyOrders(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(orderService.findOrdersForUser(currentUser));
    }

    @PostMapping("/stripe-webhooks")
    public ResponseEntity<Void> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            webhookService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok().build();
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur traitement webhook Stripe", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
