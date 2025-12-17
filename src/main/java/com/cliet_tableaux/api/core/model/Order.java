package com.cliet_tableaux.api.core.model;

import jakarta.persistence.*;

import java.io.Serial;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 2674830049619227690L;

    @Column(name = "checkout_session_id")
    private String checkoutSessionId;

    @Column(name = "state")
    private String state;

    @Column(name = "amount_cents")
    private Integer amountCents;

    @Column(name = "address")
    private String address;

    @Column(name = "slug")
    private String slug;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToOne
    @JoinColumn(name = "painting_id")
    private Painting painting;

    public String getCheckoutSessionId() {
        return checkoutSessionId;
    }

    public String getState() {
        return state;
    }

    public Integer getAmountCents() {
        return amountCents;
    }

    public String getAddress() {
        return address;
    }

    public String getSlug() {
        return slug;
    }

    public User getUser() {
        return user;
    }

    public Painting getPainting() {
        return painting;
    }
}

