package com.venn.velocity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per customer. Its row lock serialises load decisions for that customer (see {@code FundLoadService}).
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @Column(name = "customer_id", length = 255)
    private String customerId;

    protected Customer() {
        // for JPA
    }

    public Customer(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
