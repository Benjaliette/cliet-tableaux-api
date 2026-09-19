package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.daos.OrderDao;
import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionRequest;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionResponse;
import com.cliet_tableaux.api.core.dtos.OrderSummaryDto;
import com.cliet_tableaux.api.core.enums.PaymentStatutEnum;
import com.cliet_tableaux.api.core.exceptions.PaintingAlreadySoldException;
import com.cliet_tableaux.api.core.exceptions.ResourceNotFoundException;
import com.cliet_tableaux.api.core.model.Order;
import com.cliet_tableaux.api.core.model.Painting;
import com.cliet_tableaux.api.core.model.User;
import com.cloudinary.Cloudinary;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.BillingAddressCollection;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

  private static final int STRIPE_METADATA_VALUE_MAX_LENGTH = 500;

  private final OrderDao orderDao;
  private final PaintingDao paintingDao;
  private final Cloudinary cloudinary;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  public OrderService(OrderDao orderDao, PaintingDao paintingDao, Cloudinary cloudinary) {
    this.orderDao = orderDao;
    this.paintingDao = paintingDao;
    this.cloudinary = cloudinary;
  }

  @Transactional(rollbackFor = StripeException.class)
  public CheckoutSessionResponse createCheckoutSession(final User user, final CheckoutSessionRequest request) throws StripeException {
    Painting painting = paintingDao.findById(request.paintingId()).orElseThrow(
        () -> new ResourceNotFoundException(String.format("Painting id %s non trouvée", request.paintingId())));

    if (Boolean.TRUE.equals(painting.isSell())) {
      throw new PaintingAlreadySoldException(
          String.format("Painting id %s est déjà vendu", request.paintingId()));
    }

    Order order = Order.create(request.amount(), request.address(), request.currency(), user, painting);
    order = orderDao.save(order);

    List<Painting> orderPaintings = List.of(painting);

    SessionCreateParams.LineItem.PriceData.ProductData.Builder productDataBuilder =
        SessionCreateParams.LineItem.PriceData.ProductData.builder()
            .setName(painting.getTitle());

    String lineItemDescription = buildLineItemDescription(painting);
    if (lineItemDescription != null) {
      productDataBuilder.setDescription(lineItemDescription);
    }

    String imageUrl = buildPublicImageUrl(painting);
    if (imageUrl != null) {
      productDataBuilder.addImage(imageUrl);
    }

    SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
        .setMode(Mode.PAYMENT)
        .setSuccessUrl(frontendUrl + "/checkout/success?session_id={CHECKOUT_SESSION_ID}")
        .setCancelUrl(frontendUrl + "/checkout/cancel")
        .setCustomerEmail(user.getEmail())
        .setBillingAddressCollection(BillingAddressCollection.REQUIRED)
        .addLineItem(
            SessionCreateParams.LineItem.builder().setPriceData(
                SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(order.getCurrency().toLowerCase())
                    .setUnitAmount(order.getAmountCents())
                    .setProductData(productDataBuilder.build())
                    .build()
            )
                .setQuantity(1L)
                .build()
        )
        .putMetadata("orderId", String.valueOf(order.getId()))
        .putMetadata("userEmail", user.getEmail())
        .putMetadata("paintingIds", joinForStripeMetadata(orderPaintings.stream()
            .map(p -> String.valueOf(p.getId()))
            .toList()))
        .putMetadata("paintingTitles", joinForStripeMetadata(orderPaintings.stream()
            .map(Painting::getTitle)
            .toList()));

    SessionCreateParams.ShippingAddressCollection.Builder shippingBuilder =
        SessionCreateParams.ShippingAddressCollection.builder();

    Stream.of("FR").forEach((final String country) -> {
      shippingBuilder.addAllowedCountry(
          SessionCreateParams.ShippingAddressCollection.AllowedCountry.valueOf(country)
      );
    });
    paramsBuilder.setShippingAddressCollection(shippingBuilder.build());

    paramsBuilder.setPaymentIntentData(
        SessionCreateParams.PaymentIntentData.builder()
            .putMetadata("orderId", String.valueOf(order.getId()))
            .build()
    );

    Session session = Session.create(paramsBuilder.build());

    order.setCheckoutSessionId(session.getId());
    orderDao.save(order);

    return new CheckoutSessionResponse(session.getId(), session.getUrl(), order.getId());
  }

  private static String buildLineItemDescription(Painting painting) {
    StringBuilder description = new StringBuilder();
    if (StringUtils.isNotBlank(painting.getTechnique())) {
      description.append(painting.getTechnique());
    }
    if (painting.getWidth() != null && painting.getHeight() != null) {
      if (!description.isEmpty()) {
        description.append(" — ");
      }
      description.append(painting.getWidth()).append("x").append(painting.getHeight()).append("cm");
    }
    return !description.isEmpty() ? description.toString() : null;
  }

  private String buildPublicImageUrl(Painting painting) {
    if (StringUtils.isBlank(painting.getImagePublicId())) {
      return null;
    }
    return cloudinary.url().secure(true).generate(painting.getImagePublicId());
  }

  private static String joinForStripeMetadata(List<String> values) {
    String joined = String.join(",", values);
    if (joined.length() <= STRIPE_METADATA_VALUE_MAX_LENGTH) {
      return joined;
    }

    String suffix = ",...";
    int limit = STRIPE_METADATA_VALUE_MAX_LENGTH - suffix.length();
    int cut = joined.lastIndexOf(',', limit);
    return (cut > 0 ? joined.substring(0, cut) : joined.substring(0, limit)) + suffix;
  }

  public void handleCheckoutSessionCompleted(Session session) {
    Order order = orderDao.findByStripeSessionId(session.getId())
        .orElseThrow(() -> new ResourceNotFoundException(String.format("Order id %s non trouvée", session.getId())));

    order.setPaymentIntentId(session.getPaymentIntent());
    order.setState(PaymentStatutEnum.PROCESSING);
    orderDao.save(order);
  }

  private Order findOrderByPaymentIntent(PaymentIntent paymentIntent) {
    String orderId = paymentIntent.getMetadata() != null ? paymentIntent.getMetadata().get("orderId") : null;
    if (orderId == null) {
      throw new ResourceNotFoundException(
          String.format("PaymentIntent %s sans metadata orderId, Order introuvable", paymentIntent.getId()));
    }
    return orderDao.findById(Long.valueOf(orderId))
        .orElseThrow(() -> new ResourceNotFoundException(String.format("Order id %s non trouvée", orderId)));
  }

  @Transactional
  public void handlePaymentIntentSucceeded(PaymentIntent paymentIntent) {
    Order order = findOrderByPaymentIntent(paymentIntent);

    if (order.getState() == PaymentStatutEnum.SUCCEEDED) {
      logger.debug("payment_intent.succeeded déjà traité pour l'Order {}, rejeu webhook ignoré", order.getId());
      return;
    }

    order.setState(PaymentStatutEnum.SUCCEEDED);
    orderDao.save(order);

    Painting painting = order.getPainting();

    if (Boolean.TRUE.equals(painting.isSell())) {
      logger.warn("Order {} payé avec succès mais Painting {} déjà marqué vendu par une autre commande : "
              + "vente en doublon, stock non modifié, à traiter manuellement",
          order.getId(), painting.getId());
      return;
    }

    painting.setSell(true);
    paintingDao.save(painting);
  }

  public void handlePaymentIntentFailed(PaymentIntent paymentIntent) {
    Order order = findOrderByPaymentIntent(paymentIntent);

    order.setState(PaymentStatutEnum.FAILED);
    orderDao.save(order);
  }

  public List<OrderSummaryDto> findOrdersForUser(User user) {
    return orderDao.findByUserOrderByCreatedAtDesc(user).stream()
        .map(OrderService::toSummaryDto)
        .toList();
  }

  private static OrderSummaryDto toSummaryDto(Order order) {
    Painting painting = order.getPainting();

    return new OrderSummaryDto(
        order.getId(),
        order.getState() != null ? order.getState().name() : null,
        order.getAmountCents(),
        order.getCurrency(),
        order.getCreatedAt(),
        painting != null ? painting.getId() : null,
        painting != null ? painting.getTitle() : null,
        painting != null ? painting.getImagePublicId() : null
    );
  }
}
