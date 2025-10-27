package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.PaymentConfig;
import com.yourcompany.ludo.repository.PaymentConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentConfigService {

    private final PaymentConfigRepository paymentConfigRepository;

    @Autowired
    public PaymentConfigService(PaymentConfigRepository paymentConfigRepository) {
        this.paymentConfigRepository = paymentConfigRepository;
    }

    // Save or update a config (case-insensitive)
    public PaymentConfig saveOrUpdate(String method, String number) {
        return paymentConfigRepository.findByMethodIgnoreCase(method)
                .map(config -> {
                    config.setNumber(number);
                    return paymentConfigRepository.save(config);
                })
                .orElseGet(() -> paymentConfigRepository.save(new PaymentConfig(method, number)));
    }

    // Update only number (case-insensitive)
    public PaymentConfig updateNumber(String method, String number) {
        PaymentConfig config = paymentConfigRepository.findByMethodIgnoreCase(method)
                .orElseThrow(() -> new RuntimeException("Payment method not found: " + method));
        config.setNumber(number);
        return paymentConfigRepository.save(config);
    }

    // Get config by method
    public Optional<PaymentConfig> getConfig(String method) {
        return paymentConfigRepository.findByMethodIgnoreCase(method);
    }

    // Get all configs
    public List<PaymentConfig> getAllConfigs() {
        return paymentConfigRepository.findAll();
    }

    // Delete config
    public void deleteConfig(String method) {
        PaymentConfig config = paymentConfigRepository.findByMethodIgnoreCase(method)
                .orElseThrow(() -> new RuntimeException("Payment method not found: " + method));
        paymentConfigRepository.delete(config);
    }
}
