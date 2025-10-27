package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.WithdrawRequestRepository;
import com.yourcompany.ludo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WithdrawService {

    @Autowired
    private WithdrawRequestRepository withdrawRequestRepository;

    @Autowired
    private UserRepository userRepository;

    public WithdrawRequest createWithdrawRequest(WithdrawRequest request) {
        request.setStatus(WithdrawRequest.Status.PENDING);
        request.setRequestedAt(LocalDateTime.now());
        return withdrawRequestRepository.save(request);
    }

    public List<WithdrawRequest> getAll() {
        return withdrawRequestRepository.findAll();
    }

    public List<WithdrawRequest> getByUser(User user) {
        return withdrawRequestRepository.findByUser(user);
    }

    public List<WithdrawRequest> getPending() {
        return withdrawRequestRepository.findByStatus(WithdrawRequest.Status.PENDING);
    }

    public WithdrawRequest approve(Long id, String txnId) throws Exception {
        WithdrawRequest request = withdrawRequestRepository.findById(id)
                .orElseThrow(() -> new Exception("Withdraw not found"));

        if (request.getStatus() != WithdrawRequest.Status.PENDING) {
            throw new Exception("Withdraw already processed");
        }

        // Ensure unique transaction ID
        txnId = prepareTxnId(txnId);

        // Add lifetime earnings if not already counted
        if (Boolean.FALSE.equals(request.getCountedInLifetime())) {
            User user = request.getUser();
            BigDecimal lifetime = user.getLifetimeEarnings() == null ? BigDecimal.ZERO : user.getLifetimeEarnings();
            user.setLifetimeEarnings(lifetime.add(request.getAmount()));
            userRepository.save(user);
            request.setCountedInLifetime(true);
        }

        request.setStatus(WithdrawRequest.Status.APPROVED);
        request.setTransactionId(txnId);
        request.setApprovedAt(LocalDateTime.now());

        return withdrawRequestRepository.save(request);
    }

    public WithdrawRequest reject(Long id, String txnId) throws Exception {
        WithdrawRequest request = withdrawRequestRepository.findById(id)
                .orElseThrow(() -> new Exception("Withdraw not found"));

        if (request.getStatus() != WithdrawRequest.Status.PENDING) {
            throw new Exception("Withdraw already processed");
        }

        // Refund balance
        User user = request.getUser();
        BigDecimal balance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        user.setBalance(balance.add(request.getAmount()));
        userRepository.save(user);

        // Ensure unique transaction ID
        txnId = prepareTxnId(txnId);

        request.setTransactionId(txnId);
        request.setStatus(WithdrawRequest.Status.REJECTED);
        request.setRejectedAt(LocalDateTime.now());
        request.setCountedInLifetime(false);

        return withdrawRequestRepository.save(request);
    }

    private String prepareTxnId(String txnId) throws Exception {
        if (txnId == null || txnId.trim().isEmpty()) {
            txnId = generateUniqueTxnId();
        } else if (existsByTxnId(txnId)) {
            throw new Exception("Transaction ID already exists");
        }
        return txnId;
    }

    public boolean existsByTxnId(String txnId) {
        return withdrawRequestRepository.existsByTransactionId(txnId);
    }

    public String generateUniqueTxnId() {
        SecureRandom random = new SecureRandom();
        String txnId;
        do {
            int number = 10000000 + random.nextInt(90000000);
            txnId = "MG" + number;
        } while (existsByTxnId(txnId));
        return txnId;
    }

    public BigDecimal getTotalWithdrawn(User user) {
        return withdrawRequestRepository.findByUser(user).stream()
                .filter(w -> w.getStatus() == WithdrawRequest.Status.APPROVED)
                .map(WithdrawRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public WithdrawRequest save(WithdrawRequest request) {
        return withdrawRequestRepository.save(request);
    }
}
