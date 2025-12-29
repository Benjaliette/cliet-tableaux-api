package com.cliet_tableaux.api.core.services;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {

  private final OrderService orderService;

  @Value("${stripe.webhook-secret}")
  private String webhookSecret;

  public WebhookService(OrderService orderService) {
    this.orderService = orderService;
  }

  public void handleWebhook(String payload, String sigHeader) {
    Event event;

    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (Exception e) {
      throw new RuntimeException("Invalid webhook signature");
    }

    switch (event.getType()) {
      case "checkout.session.completed":
        Session session = (Session) event.getDataObjectDeserializer()
            .getObject()
            .orElseThrow(() -> new RuntimeException("Failed to deserialize session"));
        orderService.handleCheckoutSessionCompleted(session);
        break;

      case "payment_intent.succeeded":
        PaymentIntent successIntent = (PaymentIntent) event.getDataObjectDeserializer()
            .getObject()
            .orElseThrow(() -> new RuntimeException("Failed to deserialize payment intent"));
        orderService.handlePaymentIntentSucceeded(successIntent);
        break;

      case "payment_intent.payment_failed":
        PaymentIntent failedIntent = (PaymentIntent) event.getDataObjectDeserializer()
            .getObject()
            .orElseThrow(() -> new RuntimeException("Failed to deserialize payment intent"));
        orderService.handlePaymentIntentFailed(failedIntent);
        break;

      default:
        break;
    }
  }
}
