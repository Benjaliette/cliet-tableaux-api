package com.cliet_tableaux.api.core.model;

import com.cliet_tableaux.api.core.enums.PaymentStatutEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serial;
import org.apache.commons.lang3.StringUtils;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 2674830049619227690L;

    @Column(name = "checkout_session_id")
    private String checkoutSessionId;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private PaymentStatutEnum state;

    @Column(name = "amount_cents")
    private final Long amountCents;

    @Column(name = "address")
    private final String address;

    @Column(name = "currency")
    private final String currency;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private final User user;

    @OneToOne
    @JoinColumn(name = "painting_id")
    private final Painting painting;

    private Order(PaymentStatutEnum state, Long amountCents, String address, String currency, User user,
        Painting painting) {
        this.state = state;
        this.amountCents = amountCents;
        this.address = address;
        this.currency = currency;
        this.user = user;
        this.painting = painting;
    }

    public static Order create(final Long amountCents, final String address, String currency, final User user,
        final Painting painting) {
        if (StringUtils.isBlank(currency)) {
            currency = "EUR";
        }
        return new Order(PaymentStatutEnum.PENDING, amountCents, address, currency, user, painting);
    }

    // GETTERS
    public String getCheckoutSessionId() {
        return checkoutSessionId;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public PaymentStatutEnum getState() {
        return state;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public String getAddress() {
        return address;
    }

    public String getCurrency() {
        return currency;
    }

    public User getUser() {
        return user;
    }

    public Painting getPainting() {
        return painting;
    }

    // SETTERS
    public void setCheckoutSessionId(String checkoutSessionId) {
        this.checkoutSessionId = checkoutSessionId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public void setState(PaymentStatutEnum state) {
        this.state = state;
    }
}

