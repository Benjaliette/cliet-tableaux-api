package com.cliet_tableaux.api.core.services;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.util.Optional;
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

  public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {
    Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

    switch (event.getType()) {
      case "checkout.session.completed":
        Session session = (Session) deserializeEventObject(event, "session");
        orderService.handleCheckoutSessionCompleted(session);
        break;

      case "payment_intent.succeeded":
        PaymentIntent successIntent = (PaymentIntent) deserializeEventObject(event, "payment intent");
        orderService.handlePaymentIntentSucceeded(successIntent);
        break;

      case "payment_intent.payment_failed":
        PaymentIntent failedIntent = (PaymentIntent) deserializeEventObject(event, "payment intent");
        orderService.handlePaymentIntentFailed(failedIntent);
        break;

      default:
        break;
    }
  }

  private StripeObject deserializeEventObject(Event event, String label) {
    Optional<StripeObject> viaGetObject = event.getDataObjectDeserializer().getObject();
    if (viaGetObject.isPresent()) {
      return viaGetObject.get();
    }

    try {
      return event.getDataObjectDeserializer().deserializeUnsafe();
    } catch (EventDataObjectDeserializationException e) {
      throw new RuntimeException("Failed to deserialize " + label, e);
    }
  }
}
