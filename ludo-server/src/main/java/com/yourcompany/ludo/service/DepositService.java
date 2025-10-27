package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.DepositRequestRepository;
import com.yourcompany.ludo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DepositService {

    @Autowired
    private DepositRequestRepository depositRequestRepository;

    @Autowired
    private UserRepository userRepository;

    // নতুন ডিপোজিট রিকোয়েস্ট তৈরি
    public DepositRequest createDepositRequest(DepositRequest depositRequest) throws Exception {
        if (depositRequest.getTransactionId() != null &&
            depositRequestRepository.findByTransactionId(depositRequest.getTransactionId()).isPresent()) {
            throw new Exception("This Transaction ID is already used.");
        }
        depositRequest.setStatus(DepositRequest.Status.PENDING);
        return depositRequestRepository.save(depositRequest);
    }

    // সব পেন্ডিং ডিপোজিট
    public List<DepositRequest> getPendingDeposits() {
        return depositRequestRepository.findByStatus(DepositRequest.Status.PENDING);
    }

    // নির্দিষ্ট ইউজারের ডিপোজিট
    public List<DepositRequest> getDepositsByUser(User user) {
        return depositRequestRepository.findByUser(user);
    }

    // সব ডিপোজিট
    public List<DepositRequest> getAllDeposits() {
        return depositRequestRepository.findAll();
    }

    // ডিপোজিট অনুমোদন ও ব্যালেন্স আপডেট
    public DepositRequest approveDeposit(Long id) throws Exception {
        DepositRequest request = depositRequestRepository.findById(id)
                .orElseThrow(() -> new Exception("Deposit request not found"));

        request.setStatus(DepositRequest.Status.APPROVED);
        DepositRequest approved = depositRequestRepository.save(request);

        User user = request.getUser();
        if (user != null) {
            user.setBalance(user.getBalance() + request.getAmount());
            userRepository.save(user);
        }
        return approved;
    }

    // ডিপোজিট রিজেক্ট
    public DepositRequest rejectDeposit(Long id) throws Exception {
        DepositRequest request = depositRequestRepository.findById(id)
                .orElseThrow(() -> new Exception("Deposit request not found"));

        request.setStatus(DepositRequest.Status.REJECTED);
        return depositRequestRepository.save(request);
    }
}