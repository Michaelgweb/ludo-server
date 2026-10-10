package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.model.WithdrawRequest;
import com.yourcompany.ludo.repository.WithdrawRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * উত্তোলন প্রবাহ:
 *  1) রিকোয়েস্ট: টার্নওভার ০ কিনা চেক + ব্যালেন্স কাটা + রিকোয়েস্ট তৈরি (এক ট্রানজেকশনে)
 *  2) অ্যাপ্রুভ: লাইফটাইম উত্তোলন বাড়ে (ব্যালেন্সে প্রভাব নেই)
 *  3) রিজেক্ট: ব্যালেন্সে ফেরত (টার্নওভার বদলায় না)
 * অ্যাপ্রুভ/রিজেক্টে রিকোয়েস্ট রো লক হয়, তাই একই রিকোয়েস্ট দুবার প্রসেস হয় না।
 *
 * টেলিগ্রাম: রিকোয়েস্ট / অ্যাপ্রুভ / রিজেক্ট তিনটাতেই মেসেজ যায়। টেক্সট ট্রানজেকশনের ভেতরে বানানো হয়
 * (lazy সমস্যা এড়াতে), পাঠানো হয় কমিটের পরে। রোলব্যাক হলে মেসেজ যায় না, ফেল করলেও টাকার কাজে প্রভাব নেই।
 */
@Service
public class WithdrawService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawService.class);

    private final WithdrawRequestRepository withdrawRequestRepository;
    private final UserService userService;
    private final TelegramService telegram;
    private final SecureRandom random = new SecureRandom();

    public WithdrawService(WithdrawRequestRepository withdrawRequestRepository,
                           UserService userService,
                           TelegramService telegram) {
        this.withdrawRequestRepository = withdrawRequestRepository;
        this.userService = userService;
        this.telegram = telegram;
    }

    /**
     * ইউজারের উত্তোলন রিকোয়েস্ট। ইউজার রো লক করে টার্নওভার ও ব্যালেন্স চেক করে কাটে,
     * রিকোয়েস্ট তৈরি ব্যর্থ হলে পুরোটাই রোলব্যাক হয়।
     * টার্নওভার বাকি বা ব্যালেন্স কম হলে IllegalStateException।
     */
    @Transactional(rollbackFor = Exception.class)
    public WithdrawRequest requestWithdraw(String gameId, BigDecimal amount,
                                           String method, String receiverNumber) {
        userService.holdForWithdraw(gameId, amount);
        User user = userService.findByGameId(gameId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        WithdrawRequest w = new WithdrawRequest();
        w.setUser(user);
        w.setAmount(amount);
        w.setMethod(method);
        w.setReceiverNumber(receiverNumber);
        WithdrawRequest saved = createWithdrawRequest(w);

        telegramAfterCommit("🆕 নতুন উইথড্র রিকোয়েস্ট\n"
                + "Request ID: " + saved.getId() + "\n"
                + "User: " + user.getGameId() + " (" + user.getMobile() + ")\n"
                + "Method: " + method + "\n"
                + "Amount: " + amount + " টাকা\n"
                + "প্রাপকের নম্বর: " + receiverNumber + "\n"
                + "বাকি ব্যালেন্স: " + user.getBalance() + " টাকা\n"
                + "Status: PENDING");
        return saved;
    }

    @Transactional
    public WithdrawRequest createWithdrawRequest(WithdrawRequest request) {
        request.setStatus(WithdrawRequest.Status.PENDING);
        request.setRequestedAt(LocalDateTime.now());
        return withdrawRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<WithdrawRequest> getAll() {
        return withdrawRequestRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<WithdrawRequest> getByUser(User user) {
        return withdrawRequestRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public List<WithdrawRequest> getPending() {
        return withdrawRequestRepository.findByStatus(WithdrawRequest.Status.PENDING);
    }

    @Transactional(rollbackFor = Exception.class)
    public WithdrawRequest approve(Long id, String txnId) throws Exception {
        WithdrawRequest request = withdrawRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new Exception("Withdraw not found"));

        if (request.getStatus() != WithdrawRequest.Status.PENDING) {
            throw new Exception("Withdraw already processed");
        }

        txnId = prepareTxnId(txnId);

        // লাইফটাইম উত্তোলন (শুধু দেখানোর জন্য), একবারই গোনা হবে
        if (!Boolean.TRUE.equals(request.getCountedInLifetime())) {
            userService.addLifetimeWithdraw(request.getUser().getGameId(), request.getAmount());
            request.setCountedInLifetime(true);
        }

        request.setStatus(WithdrawRequest.Status.APPROVED);
        request.setTransactionId(txnId);
        request.setApprovedAt(LocalDateTime.now());

        WithdrawRequest saved = withdrawRequestRepository.save(request);

        telegramAfterCommit("✅ উইথড্র অ্যাপ্রুভ\n"
                + "Request ID: " + saved.getId() + "\n"
                + "User: " + saved.getUser().getGameId() + " (" + saved.getUser().getMobile() + ")\n"
                + "Method: " + saved.getMethod() + "\n"
                + "Amount: " + saved.getAmount() + " টাকা\n"
                + "প্রাপকের নম্বর: " + saved.getReceiverNumber() + "\n"
                + "TrxID: " + saved.getTransactionId() + "\n"
                + "Status: APPROVED");
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public WithdrawRequest reject(Long id, String txnId) throws Exception {
        WithdrawRequest request = withdrawRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new Exception("Withdraw not found"));

        if (request.getStatus() != WithdrawRequest.Status.PENDING) {
            throw new Exception("Withdraw already processed");
        }

        txnId = prepareTxnId(txnId);

        // টাকা ব্যালেন্সে ফেরত (টার্নওভার বদলায় না)
        userService.refundWithdraw(request.getUser().getGameId(), request.getAmount());

        request.setTransactionId(txnId);
        request.setStatus(WithdrawRequest.Status.REJECTED);
        request.setRejectedAt(LocalDateTime.now());
        request.setCountedInLifetime(false);

        WithdrawRequest saved = withdrawRequestRepository.save(request);

        telegramAfterCommit("❌ উইথড্র রিজেক্ট (টাকা ব্যালেন্সে ফেরত)\n"
                + "Request ID: " + saved.getId() + "\n"
                + "User: " + saved.getUser().getGameId() + " (" + saved.getUser().getMobile() + ")\n"
                + "Method: " + saved.getMethod() + "\n"
                + "Amount: " + saved.getAmount() + " টাকা\n"
                + "Status: REJECTED");
        return saved;
    }

    private String prepareTxnId(String txnId) throws Exception {
        if (txnId == null || txnId.trim().isEmpty()) {
            return generateUniqueTxnId();
        }
        txnId = txnId.trim();
        if (existsByTxnId(txnId)) {
            throw new Exception("Transaction ID already exists");
        }
        return txnId;
    }

    @Transactional(readOnly = true)
    public boolean existsByTxnId(String txnId) {
        return withdrawRequestRepository.existsByTransactionId(txnId);
    }

    public String generateUniqueTxnId() {
        String txnId;
        do {
            int number = 10000000 + random.nextInt(90000000);
            txnId = "MG" + number;
        } while (existsByTxnId(txnId));
        return txnId;
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalWithdrawn(User user) {
        return withdrawRequestRepository.findByUser(user).stream()
                .filter(w -> w.getStatus() == WithdrawRequest.Status.APPROVED)
                .map(WithdrawRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public WithdrawRequest save(WithdrawRequest request) {
        return withdrawRequestRepository.save(request);
    }

    // ---------------------------------------------------------------
    /** টেলিগ্রাম: কমিটের পরে, ফেল করলেও টাকার কাজে প্রভাব নেই */
    private void telegramAfterCommit(String text) {
        afterCommit(() -> {
            try {
                telegram.send(text);
            } catch (Exception e) {
                log.warn("Telegram notify failed: {}", e.getMessage());
            }
        });
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else {
            r.run();
        }
    }
}
