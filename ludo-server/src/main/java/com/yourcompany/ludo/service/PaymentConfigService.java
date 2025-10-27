package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.PaymentConfig;
import com.yourcompany.ludo.repository.PaymentConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentConfigService {

    @Autowired
    private PaymentConfigRepository paymentConfigRepository;

    // Save or update payment method
    public PaymentConfig saveOrUpdate(String method, String number) {
        Optional<PaymentConfig> existing = paymentConfigRepository.findByMethod(method);
        if (existing.isPresent()) {
            PaymentConfig config = existing.get();
            config.setNumber(number);
            return paymentConfigRepository.save(config);
        } else {
            return paymentConfigRepository.save(new PaymentConfig(method, number));
        }
    }

    // Get config for a method
    public Optional<PaymentConfig> getConfig(String method) {
        return paymentConfigRepository.findByMethod(method);
    }

    // Get all configs
    public List<PaymentConfig> getAllConfigs() {
        return paymentConfigRepository.findAll();
    }
}
