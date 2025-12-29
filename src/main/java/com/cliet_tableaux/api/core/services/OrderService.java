package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.daos.OrderDao;
import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionRequest;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionResponse;
import com.cliet_tableaux.api.core.enums.PaymentStatutEnum;
import com.cliet_tableaux.api.core.exceptions.ResourceNotFoundException;
import com.cliet_tableaux.api.core.model.Order;
import com.cliet_tableaux.api.core.model.Painting;
import com.cliet_tableaux.api.core.model.User;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.BillingAddressCollection;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private final OrderDao orderDao;
  private final PaintingDao paintingDao;
  private final UserDao userDao;

  @Value("${app.baseUrl}")
  private String baseUrl;

  public OrderService(OrderDao orderDao, PaintingDao paintingDao, UserDao userDao) {
    this.orderDao = orderDao;
    this.paintingDao = paintingDao;
    this.userDao = userDao;
  }

  public CheckoutSessionResponse createCheckoutSession(final CheckoutSessionRequest request) throws StripeException {
    Painting painting = paintingDao.findById(request.paintingId()).orElseThrow(
        () -> new ResourceNotFoundException(String.format("Painting id %s non trouvée", request.paintingId())));
    User user = userDao.findById(request.userId())
        .orElseThrow(() -> new ResourceNotFoundException(String.format("User id %s non trouvé", request.userId())));

    Order order = Order.create(request.amount(), request.address(), request.currency(), user, painting);
    order = orderDao.save(order);

    SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
        .setMode(Mode.PAYMENT)
        .setSuccessUrl(baseUrl + "/")
        .setCancelUrl(baseUrl + "/")
        .setCustomerEmail(user.getEmail())
        .setBillingAddressCollection(BillingAddressCollection.REQUIRED)
        .addLineItem(
            SessionCreateParams.LineItem.builder().setPriceData(
                SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(order.getCurrency().toLowerCase())
                    .setUnitAmount(order.getAmountCents())
                    .setProductData(
                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(painting.getTitle())
                            .setDescription(painting.getDescription())
                            .build()
                    )
                    .build()
            )
                .setQuantity(1L)
                .build()
        );

    // Ajouter pays autorisés pour la collecte de l'adresse de shipping
    SessionCreateParams.ShippingAddressCollection.Builder shippingBuilder =
        SessionCreateParams.ShippingAddressCollection.builder();

    Stream.of("FR", "BE", "DE", "ES", "IT", "NL", "LU", "CH").forEach((final String country) -> {
      shippingBuilder.addAllowedCountry(
          SessionCreateParams.ShippingAddressCollection.AllowedCountry.valueOf(country)
      );
    });
    paramsBuilder.setShippingAddressCollection(shippingBuilder.build());

    Session session = Session.create(paramsBuilder.build());

    order.setCheckoutSessionId(session.getId());
    orderDao.save(order);

    return new CheckoutSessionResponse(session.getId(), session.getUrl(), order.getId());
  }

  public void handleCheckoutSessionCompleted(Session session) {
    Order order = orderDao.findByStripeSessionId(session.getId())
        .orElseThrow(() -> new ResourceNotFoundException(String.format("Order id %s non trouvée", session.getId())));

    order.setState(PaymentStatutEnum.PROCESSING);
    orderDao.save(order);
  }

  public void handlePaymentIntentSucceeded(PaymentIntent paymentIntent) {
    Order order = orderDao.findByStripePaymentIntentId(paymentIntent.getId())
        .orElseThrow(() -> new ResourceNotFoundException(String.format("Order id %s non trouvée", paymentIntent.getId())));

    order.setState(PaymentStatutEnum.SUCCEEDED);
    orderDao.save(order);
  }

  public void handlePaymentIntentFailed(PaymentIntent paymentIntent) {
    Order order = orderDao.findByStripePaymentIntentId(paymentIntent.getId())
        .orElseThrow(() -> new ResourceNotFoundException(String.format("Order id %s non trouvée", paymentIntent.getId())));

    order.setState(PaymentStatutEnum.FAILED);
    orderDao.save(order);
  }
}
