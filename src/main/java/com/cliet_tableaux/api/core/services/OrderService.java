package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.daos.OrderDao;
import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionRequest;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionResponse;
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

  // Limite Stripe : 500 caractères max par valeur de metadata (le nombre de clés, lui, reste
  // fixe ici à 4 quel que soit le contenu de la commande, donc pas de risque sur la limite des
  // 50 clés max par objet).
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

  // rollbackFor est nécessaire car StripeException est une exception checked :
  // sans lui, Spring ne fait rollback que sur les RuntimeException, et l'Order créé
  // juste avant l'appel Stripe resterait en base sans checkoutSessionId (commande orpheline).
  @Transactional(rollbackFor = StripeException.class)
  public CheckoutSessionResponse createCheckoutSession(final User user, final CheckoutSessionRequest request) throws StripeException {
    Painting painting = paintingDao.findById(request.paintingId()).orElseThrow(
        () -> new ResourceNotFoundException(String.format("Painting id %s non trouvée", request.paintingId())));

    // Anti-survente : simple vérification en lecture du flag `sell`, positionné manuellement
    // pour l'instant (pas d'automatisation de la vente à ce stade, cf. Phase 4).
    if (Boolean.TRUE.equals(painting.isSell())) {
      throw new PaintingAlreadySoldException(
          String.format("Painting id %s est déjà vendu", request.paintingId()));
    }

    Order order = Order.create(request.amount(), request.address(), request.currency(), user, painting);
    order = orderDao.save(order);

    // Order ne porte qu'un seul Painting (@OneToOne) : pas de commande multi-tableaux dans le
    // modèle actuel. Formaté en liste ici uniquement pour produire les metadata "paintingIds"/
    // "paintingTitles" au format demandé (valeurs séparées par des virgules), et rester valable
    // sans changement si le modèle évoluait un jour vers plusieurs tableaux par commande.
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
        // Metadata sur la Session (dashboard Stripe / suivi), distinctes de la metadata orderId
        // posée plus bas sur le PaymentIntent (utilisée par les webhooks, cf. handlePaymentIntentSucceeded).
        .putMetadata("orderId", String.valueOf(order.getId()))
        .putMetadata("userEmail", user.getEmail())
        .putMetadata("paintingIds", joinForStripeMetadata(orderPaintings.stream()
            .map(p -> String.valueOf(p.getId()))
            .toList()))
        .putMetadata("paintingTitles", joinForStripeMetadata(orderPaintings.stream()
            .map(Painting::getTitle)
            .toList()));

    // Ajouter pays autorisés pour la collecte de l'adresse de shipping
    SessionCreateParams.ShippingAddressCollection.Builder shippingBuilder =
        SessionCreateParams.ShippingAddressCollection.builder();

    Stream.of("FR", "BE", "DE", "ES", "IT", "NL", "LU", "CH").forEach((final String country) -> {
      shippingBuilder.addAllowedCountry(
          SessionCreateParams.ShippingAddressCollection.AllowedCountry.valueOf(country)
      );
    });
    paramsBuilder.setShippingAddressCollection(shippingBuilder.build());

    // Metadata posée directement sur le PaymentIntent que Stripe va créer (pas sur la Session) :
    // Stripe ne garantit pas l'ordre de livraison de checkout.session.completed et
    // payment_intent.succeeded, donc handlePaymentIntentSucceeded/Failed ne peuvent pas dépendre
    // d'un paymentIntentId renseigné par l'autre event. En retrouvant l'Order directement par son
    // id via cette metadata, le lookup ne dépend plus d'aucun ordre d'arrivée.
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

  // Painting ne porte pas de champ "artiste" (catalogue mono-artiste, pas de notion d'auteur par
  // œuvre) : on affiche donc la technique et les dimensions, seules données descriptives
  // disponibles sur l'entité, plutôt que le texte libre `description` (déjà utilisé ailleurs).
  private static String buildLineItemDescription(Painting painting) {
    StringBuilder description = new StringBuilder();
    if (StringUtils.isNotBlank(painting.getTechnique())) {
      description.append(painting.getTechnique());
    }
    if (painting.getWidth() != null && painting.getHeight() != null) {
      if (description.length() > 0) {
        description.append(" — ");
      }
      description.append(painting.getWidth()).append("x").append(painting.getHeight()).append("cm");
    }
    return description.length() > 0 ? description.toString() : null;
  }

  // `imagePublicId` est l'identifiant Cloudinary de l'image (cf. CloudinaryConfig/CloudinaryController),
  // pas une URL : il faut le résoudre en URL absolue via le SDK Cloudinary pour que Stripe Checkout
  // puisse l'afficher (Stripe exige une URL publique valide, pas un chemin relatif ou un id).
  private String buildPublicImageUrl(Painting painting) {
    if (StringUtils.isBlank(painting.getImagePublicId())) {
      return null;
    }
    return cloudinary.url().secure(true).generate(painting.getImagePublicId());
  }

  // Tronque proprement sur la dernière virgule complète plutôt qu'au milieu d'un id/titre, pour
  // rester sous la limite Stripe de 500 caractères par valeur de metadata si une commande venait
  // à porter beaucoup d'articles.
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

    // Conservé pour traçabilité/support (ex: retrouver une Order depuis le dashboard Stripe),
    // mais n'est plus utilisé pour le lookup dans handlePaymentIntentSucceeded/Failed : voir
    // le commentaire sur payment_intent_data.metadata dans createCheckoutSession.
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

  // @Transactional garantit que le passage de l'Order à SUCCEEDED et la mise à jour du
  // stock (Painting.sell) sont appliqués ensemble ou pas du tout.
  @Transactional
  public void handlePaymentIntentSucceeded(PaymentIntent paymentIntent) {
    Order order = findOrderByPaymentIntent(paymentIntent);

    // Idempotence : Stripe peut renvoyer plusieurs fois le même événement webhook (retry).
    // Si cette commande est déjà SUCCEEDED, la mise à jour (statut + stock) a déjà été
    // appliquée lors d'un précédent passage : on ne rejoue rien.
    if (order.getState() == PaymentStatutEnum.SUCCEEDED) {
      logger.debug("payment_intent.succeeded déjà traité pour l'Order {}, rejeu webhook ignoré", order.getId());
      return;
    }

    order.setState(PaymentStatutEnum.SUCCEEDED);
    orderDao.save(order);

    Painting painting = order.getPainting();

    // Anti-survente, 2e vérification juste avant la mise à jour du stock (la 1re a lieu à la
    // création de la commande, cf. createCheckoutSession) : si le Painting est déjà marqué vendu
    // alors que CETTE commande n'était pas encore SUCCEEDED, une autre commande l'a vendu
    // entre-temps (double vente). On ne "re-vend" pas silencieusement : le paiement reste
    // enregistré (le client a bien payé), mais le stock n'est pas retouché, et le cas est loggé
    // pour traitement manuel (remboursement éventuel) plutôt que masqué.
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
}
