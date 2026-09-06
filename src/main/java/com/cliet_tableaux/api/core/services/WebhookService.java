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

  // SignatureVerificationException est laissée remonter telle quelle (au lieu d'être ré-enveloppée
  // dans une RuntimeException générique) pour que OrderController puisse la distinguer des autres
  // erreurs : une signature invalide est définitive (400, Stripe ne doit pas retenter), alors qu'une
  // erreur inattendue plus loin (order introuvable, désérialisation) doit renvoyer 500 pour que
  // Stripe retente l'envoi du webhook plus tard.
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

  // getObject() renvoie Optional.empty() dès que l'api_version de l'événement Stripe (celle
  // configurée sur le compte/endpoint, souvent différente en dev via `stripe listen`) ne correspond
  // pas exactement à Stripe.API_VERSION figée dans stripe-java — même si le JSON est par ailleurs
  // parfaitement valide. C'est le cas de figure rencontré ici : diagnostiqué en reproduisant l'appel
  // avec un event.apiVersion volontairement différent de Stripe.API_VERSION, ce qui donne
  // exactement le RuntimeException("Failed to deserialize ...") observé en production.
  // deserializeUnsafe() force la désérialisation malgré l'écart de version (recommandation Stripe) ;
  // acceptable ici car on ne lit que des champs stables de Session/PaymentIntent (id).
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
