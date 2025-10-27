package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.WithdrawRequestRepository;
import com.yourcompany.ludo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public List<WithdrawRequest> getAll() { return withdrawRequestRepository.findAll(); }

    public List<WithdrawRequest> getByUser(User user) { return withdrawRequestRepository.findByUser(user); }

    public List<WithdrawRequest> getPending() { return withdrawRequestRepository.findByStatus(WithdrawRequest.Status.PENDING); }

    public WithdrawRequest approve(Long id, String txnId) throws Exception {
        WithdrawRequest request = withdrawRequestRepository.findById(id)
                .orElseThrow(() -> new Exception("Withdraw not found"));

        if (request.getStatus() != WithdrawRequest.Status.PENDING) {
            throw new Exception("Withdraw already processed");
        }

        if (txnId == null || txnId.trim().isEmpty()) {
            txnId = generateUniqueTxnId();
        } else if (existsByTxnId(txnId)) {
            throw new Exception("Transaction ID already exists");
        }

        if (!request.getCountedInLifetime()) {
            User user = request.getUser();
            user.setLifetimeEarnings(user.getLifetimeEarnings() + request.getAmount());
            userRepository.save(user);
            request.setCountedInLifetime(true);
        }

        request.setStatus(WithdrawRequest.Status.APPROVED);
        request.setTransactionId(txnId);
        request.setRequestedAt(LocalDateTime.now());

        return withdrawRequestRepository.save(request);
    }

    public WithdrawRequest reject(Long id, String txnId) throws Exception {
        WithdrawRequest request = withdrawRequestRepository.findById(id)
                .orElseThrow(() -> new Exception("Withdraw not found"));

        if (request.getStatus() != WithdrawRequest.Status.PENDING) {
            throw new Exception("Withdraw already processed");
        }

        User user = request.getUser();
        user.setBalance(user.getBalance() + request.getAmount());
        userRepository.save(user);

        if (txnId == null || txnId.trim().isEmpty()) {
            txnId = generateUniqueTxnId();
        } else if (existsByTxnId(txnId)) {
            throw new Exception("Transaction ID already exists");
        }

        request.setTransactionId(txnId);
        request.setStatus(WithdrawRequest.Status.REJECTED);
        request.setRequestedAt(LocalDateTime.now());
        request.setCountedInLifetime(false);

        return withdrawRequestRepository.save(request);
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

    public double getTotalWithdrawn(User user) {
        return withdrawRequestRepository.findByUser(user).stream()
                .filter(w -> w.getStatus() == WithdrawRequest.Status.APPROVED)
                .mapToDouble(WithdrawRequest::getAmount)
                .sum();
    }

    public WithdrawRequest save(WithdrawRequest request) {
        return withdrawRequestRepository.save(request);
    }
}
