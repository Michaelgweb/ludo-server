package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.BonusHistory;
import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.BonusHistoryRepository;
import com.yourcompany.ludo.repository.DepositRequestRepository;
import com.yourcompany.ludo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DepositService {

    private static final Logger log = LoggerFactory.getLogger(DepositService.class);

    @Autowired private DepositRequestRepository depositRequestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BonusHistoryRepository bonusHistoryRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private UserServiceImpl userService; // শুধু lock করার জন্য ব্যবহার হবে

    private static final BigDecimal REFERRAL_BONUS = new BigDecimal("40.00");

    // ---------------- Create Deposit Request ----------------
    public DepositRequest createDepositRequest(DepositRequest depositRequest) throws Exception {
        if (depositRequest.getTransactionId() != null &&
            depositRequestRepository.existsByTransactionId(depositRequest.getTransactionId())) {
            throw new Exception("This Transaction ID is already used.");
        }

        depositRequest.setStatus(DepositRequest.Status.PENDING);
        return depositRequestRepository.save(depositRequest);
    }

    // ---------------- Approve Deposit ----------------
    @Transactional(rollbackFor = Exception.class)
    public DepositRequest approveDeposit(Long id) throws Exception {
        DepositRequest request = depositRequestRepository.findById(id)
                .orElseThrow(() -> new Exception("Deposit request not found"));

        if (request.getStatus() == DepositRequest.Status.APPROVED) {
            throw new Exception("Deposit already approved");
        }

        // Lock user for safe balance update
        User user = userService.lockAndGetByGameId(request.getUser().getGameId());
        if (user == null) throw new Exception("User not found for this deposit");

        BigDecimal depositAmount = request.getAmount();

        // Update deposit status first
        request.setStatus(DepositRequest.Status.APPROVED);
        depositRequestRepository.saveAndFlush(request);

        // Update user balance
        user.setDepositBalance(user.getDepositBalance().add(depositAmount));
        userRepository.saveAndFlush(user);

        // Handle referral bonuses safely
        handleFirstDepositBonus(user, depositAmount);

        // Notify user
        messagingTemplate.convertAndSend(
                "/topic/notifications/" + user.getGameId(),
                "Your deposit of " + depositAmount + " has been approved!"
        );

        log.info("✅ Deposit approved for user {} amount {}", user.getGameId(), depositAmount);
        return request;
    }

    // ---------------- Handle First Deposit & Referral Bonus ----------------
    @Transactional(rollbackFor = Exception.class)
    public void handleFirstDepositBonus(User user, BigDecimal depositAmount) {
        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("❌ Invalid deposit amount: {}", depositAmount);
            return;
        }

        // --- First deposit flag update ---
        if (!user.isFirstDepositBonusGiven()) {
            user.setFirstDepositBonusGiven(true);
            userRepository.saveAndFlush(user);
            log.info("✅ First deposit detected for user: {}", user.getGameId());
        }

        // --- Find all pending referral bonuses for this user ---
        List<BonusHistory> pendingBonuses = bonusHistoryRepository
                .findBySourceGameIdAndTypeAndStatusOrderByCreatedAtDesc(
                        user.getGameId(),
                        "REFERRER_PENDING",
                        "PENDING"
                );

        log.info("📌 Found {} pending referral bonuses for new user {}", pendingBonuses.size(), user.getGameId());

        // --- Process and credit referrer ---
        for (BonusHistory bonus : pendingBonuses) {
            if ("PENDING".equalsIgnoreCase(bonus.getStatus())) {
                userRepository.findByGameId(bonus.getUserGameId()).ifPresent(referrer -> {
                    // 1) First mark COMPLETED
                    bonus.setStatus("COMPLETED");
                    bonusHistoryRepository.saveAndFlush(bonus);
                    log.info("✅ Referral bonus {} marked as COMPLETED", bonus.getId());

                    // 2) Then credit referrer
                    referrer.setDepositBalance(referrer.getDepositBalance().add(bonus.getAmount()));
                    userRepository.saveAndFlush(referrer);
                    log.info("💰 Credited {} to referrer {}", bonus.getAmount(), referrer.getGameId());

                    // 3) Notify referrer
                    messagingTemplate.convertAndSend(
                            "/topic/notifications/" + referrer.getGameId(),
                            "Your referral bonus of " + bonus.getAmount() + " has been credited!"
                    );
                });
            }
        }
    }

    // ---------------- Reject Deposit ----------------
    @Transactional(rollbackFor = Exception.class)
    public DepositRequest rejectDeposit(Long id) throws Exception {
        DepositRequest request = depositRequestRepository.findById(id)
                .orElseThrow(() -> new Exception("Deposit request not found"));

        if (request.getStatus() == DepositRequest.Status.REJECTED) {
            throw new Exception("Deposit already rejected");
        }

        request.setStatus(DepositRequest.Status.REJECTED);
        depositRequestRepository.saveAndFlush(request);

        User user = request.getUser();
        if (user != null) {
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + user.getGameId(),
                    "Your deposit of " + request.getAmount() + " has been rejected!"
            );
            log.info("❌ Deposit rejected for user {} amount {}", user.getGameId(), request.getAmount());
        }

        return request;
    }

    // ---------------- Extra Methods for Controller ----------------
    public List<DepositRequest> getPendingDeposits() {
        return depositRequestRepository.findByStatus(DepositRequest.Status.PENDING);
    }

    public List<DepositRequest> getDepositsByUser(User user) {
        return depositRequestRepository.findByUser(user);
    }

    public List<DepositRequest> getAllDeposits() {
        return depositRequestRepository.findAll();
    }
}
