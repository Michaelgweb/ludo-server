package com.yourcompany.ludo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_config")
public class PaymentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // bKash / Nagad
    @Column(nullable = false, unique = true)
    private String method;

    // Payment number
    @Column(nullable = false)
    private String number;

    public PaymentConfig() {}

    public PaymentConfig(String method, String number) {
        this.method = method;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}
