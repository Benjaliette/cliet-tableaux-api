package com.cliet_tableaux.api.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.io.Serial;

@Entity
@Table(name = "paintings", indexes = @Index(columnList = "id, title, description"))
public class Painting extends BaseEntity {
    @Serial
    private static final long serialVersionUID = -2557579015346495264L;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "sell")
    private Boolean sell;

    @Column(name = "price_cents")
    private Long priceCents;

    @Column(name = "price_currency")
    private String priceCurrency;

    @Column(name = "image_public_id")
    private String imagePublicId;

    @Column(name = "technique")
    private String technique;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Boolean isSell() {
        return sell;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public String getImagePublicId() {
        return imagePublicId;
    }

    public String getTechnique() {
        return technique;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSell(boolean sell) {
        this.sell = sell;
    }

    public void setPriceCents(Long priceCents) {
        this.priceCents = priceCents;
    }

    public void setPriceCurrency(String priceCurrency) {
        this.priceCurrency = priceCurrency;
    }

    public void setImagePublicId(String imagePublicId) {
        this.imagePublicId = imagePublicId;
    }

    public void setTechnique(String technique) {
        this.technique = technique;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}

