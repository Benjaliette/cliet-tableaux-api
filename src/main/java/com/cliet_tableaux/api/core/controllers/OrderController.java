package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.CheckoutSessionRequest;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionResponse;
import com.cliet_tableaux.api.core.model.User;
import com.cliet_tableaux.api.core.services.OrderService;
import com.cliet_tableaux.api.core.services.WebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    // Endpoint public : Stripe ne peut pas s'authentifier comme un utilisateur classique.
    // La sécurité est assurée par la vérification de signature Stripe dans WebhookService
    // (Webhook.constructEvent avec le secret partagé) plutôt que par un principal Spring Security.
    @PostMapping("/stripe-webhooks")
    public ResponseEntity<Void> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            webhookService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok().build();
        } catch (SignatureVerificationException e) {
            // Signature invalide : payload rejeté définitivement, Stripe ne doit pas retenter.
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur traitement webhook Stripe", e);
            // Erreur inattendue (order introuvable, désérialisation...) : 500 pour que Stripe
            // retente l'envoi du webhook plus tard, au lieu d'un 400 uniforme qui masquait la différence.
            return ResponseEntity.internalServerError().build();
        }
    }
}
