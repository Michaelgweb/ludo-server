package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.NotificationDto;
import com.yourcompany.ludo.model.DepositRequest;
import com.yourcompany.ludo.model.DepositRequest.Status;
import com.yourcompany.ludo.model.PaymentSms;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.DepositRequestRepository;
import com.yourcompany.ludo.repository.PaymentSmsRepository;
import com.yourcompany.ludo.util.PaymentRules;
import com.yourcompany.ludo.util.SmsParser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DepositService {

    public enum SmsResult { IGNORED, DUPLICATE, STORED, AUTO_APPROVED }

    private static final BigDecimal MIN = new BigDecimal("10.00");
    private static final BigDecimal MAX = new BigDecimal("500000.00");
    private static final SecureRandom RND = new SecureRandom();

    /** একজন ইউজারের একসাথে সর্বোচ্চ কতটা TrxID-ছাড়া pending ডিপোজিট থাকতে পারবে */
    private static final long MAX_OPEN_UNSUBMITTED = 3;

    /** submit আর SMS একসাথে এলে race এড়াতে (একটি সার্ভার ইনস্ট্যান্সের জন্য) */
    private static final Object MATCH_LOCK = new Object();

    private final DepositRequestRepository repo;
    private final PaymentSmsRepository smsRepo;
    private final PaymentRotationService rotation;
    private final SimpMessagingTemplate ws;
    private final UserService userService;
    private final TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    public DepositService(DepositRequestRepository repo, PaymentSmsRepository smsRepo,
                          PaymentRotationService rotation, SimpMessagingTemplate ws,
                          PlatformTransactionManager tm, UserService userService) {
        this.repo = repo;
        this.smsRepo = smsRepo;
        this.rotation = rotation;
        this.ws = ws;
        this.userService = userService;
        this.tx = new TransactionTemplate(tm);
    }

    // ============ 1) PREPARE: অটো রোটেশনে অ্যাডমিন নম্বর ============
    @Transactional
    public DepositRequest prepare(User user, String method, BigDecimal amount) {
        if (user.isBlocked()) throw err(HttpStatus.FORBIDDEN, "আপনার অ্যাকাউন্ট ব্লকড");
        amount = validateAmount(amount);
        String m = PaymentRules.normalizeMethod(method);

        // স্প্যাম ঠেকাতে: TrxID না দেওয়া অসমাপ্ত ডিপোজিট সীমিত
        Long open = em.createQuery(
                        "select count(d) from DepositRequest d "
                                + "where d.user.id = :uid and d.status = :st and d.userTransactionId is null",
                        Long.class)
                .setParameter("uid", user.getId())
                .setParameter("st", Status.PENDING)
                .getSingleResult();
        if (open >= MAX_OPEN_UNSUBMITTED) {
            throw err(HttpStatus.TOO_MANY_REQUESTS,
                    "আগের ডিপোজিট সম্পন্ন করুন বা বাতিল করুন, তারপর নতুন করুন");
        }

        PaymentRotationService.AccountDto acc = rotation.assignNext(m);

        DepositRequest d = new DepositRequest();
        d.setUser(user);
        d.setAmount(amount);
        d.setMethod(m);
        d.setPaymentAccountNumber(acc.number());
        d.setAdminAccountId(acc.id());
        d.setTransactionId(newServerTxnId());
        return repo.save(d);
    }

    // ============ 2) SUBMIT: ইউজার TrxID দেয় -> অটো ম্যাচ ============
    public DepositRequest submit(User user, Long depositId, String userTrxId) {
        synchronized (MATCH_LOCK) {
            try {
                return tx.execute(s -> doSubmit(user, depositId, userTrxId));
            } catch (DataIntegrityViolationException e) {
                throw err(HttpStatus.CONFLICT, "এই TrxID আগেই ব্যবহার হয়েছে");
            }
        }
    }

    private DepositRequest doSubmit(User user, Long depositId, String userTrxId) {
        DepositRequest d = repo.findByIdForUpdate(depositId)
                .orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Deposit not found"));
        if (!d.getUser().getId().equals(user.getId())) throw err(HttpStatus.NOT_FOUND, "Deposit not found");
        if (d.getStatus() != Status.PENDING) throw err(HttpStatus.BAD_REQUEST, "শুধু pending ডিপোজিট submit করা যায়");
        if (d.getUserTransactionId() != null) throw err(HttpStatus.CONFLICT, "TrxID আগেই submit করা হয়েছে");

        String trx = userTrxId == null ? "" : userTrxId.trim().toUpperCase();
        if (!trx.matches("[A-Z0-9]{6,30}")) throw err(HttpStatus.BAD_REQUEST, "TrxID সঠিক নয়");
        if (repo.existsByMethodAndUserTransactionId(d.getMethod(), trx))
            throw err(HttpStatus.CONFLICT, "এই TrxID আগেই ব্যবহার হয়েছে");

        d.setUserTransactionId(trx);
        d.setSubmittedAt(LocalDateTime.now());
        repo.saveAndFlush(d);

        // আগে SMS চলে এসে থাকলে এখনই ম্যাচ
        Optional<PaymentSms> sms = smsRepo.findFirstByMethodAndTrxIdAndStatus(d.getMethod(), trx, PaymentSms.Status.WAITING);
        if (sms.isPresent() && sms.get().getAmount().compareTo(d.getAmount()) == 0) {
            PaymentSms p = sms.get();
            p.setStatus(PaymentSms.Status.MATCHED);
            p.setMatchedDepositId(d.getId());
            smsRepo.save(p);
            approveInternal(d, null, true);
        } else {
            notifyAfterCommit(d.getUser().getGameId(), "আপনার ডিপোজিট রিভিউয়ের জন্য জমা হয়েছে");
        }
        return d;
    }

    // ============ 3) মোবাইল থেকে SMS -> অটো আপ্রুভ ============
    public SmsResult ingestSms(String sender, String message) {
        SmsParser.Parsed p = SmsParser.parse(sender, message);
        if (p == null) return SmsResult.IGNORED;

        synchronized (MATCH_LOCK) {
            return tx.execute(s -> {
                String m = PaymentRules.normalizeMethod(p.method());
                if (smsRepo.existsByMethodAndTrxId(m, p.trxId())) return SmsResult.DUPLICATE;

                PaymentSms sms = new PaymentSms();
                sms.setMethod(m);
                sms.setTrxId(p.trxId());
                sms.setAmount(p.amount());
                sms.setSenderNumber(p.senderNumber());
                sms.setRawMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
                smsRepo.save(sms);

                Optional<DepositRequest> dep =
                        repo.findFirstByMethodAndUserTransactionIdAndStatus(m, p.trxId(), Status.PENDING);
                if (dep.isPresent() && dep.get().getAmount().compareTo(p.amount()) == 0) {
                    sms.setStatus(PaymentSms.Status.MATCHED);
                    sms.setMatchedDepositId(dep.get().getId());
                    smsRepo.save(sms);
                    approveInternal(dep.get(), null, true);
                    return SmsResult.AUTO_APPROVED;
                }
                return SmsResult.STORED;
            });
        }
    }

    // ============ 4) অ্যাডমিন / সাপোর্ট ম্যানুয়াল ============
    @Transactional
    public DepositRequest approve(Long id, User admin, String note) {
        DepositRequest d = lockPending(id);
        d.setNote(note);
        approveInternal(d, admin.getId(), false);
        return d;
    }

    @Transactional
    public DepositRequest reject(Long id, User admin, String reason) {
        DepositRequest d = lockPending(id);
        d.setStatus(Status.REJECTED);
        d.setNote(reason);
        d.setProcessedById(admin.getId());
        d.setProcessedAt(LocalDateTime.now());
        repo.save(d);
        notifyAfterCommit(d.getUser().getGameId(), "আপনার " + d.getAmount() + " টাকার ডিপোজিট রিজেক্ট হয়েছে");
        return d;
    }

    @Transactional
    public DepositRequest cancel(Long id, User user) {
        DepositRequest d = lockPending(id);
        if (!d.getUser().getId().equals(user.getId())) throw err(HttpStatus.NOT_FOUND, "Deposit not found");
        d.setStatus(Status.CANCELLED);
        d.setProcessedAt(LocalDateTime.now());
        return repo.save(d);
    }

    // ============ Queries ============
    @Transactional(readOnly = true)
    public List<DepositRequest> myDeposits(User user) {
        return repo.findByUserOrderByIdDesc(user);
    }

    @Transactional(readOnly = true)
    public Page<DepositRequest> list(Status status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "id"));
        return status == null ? repo.findAll(pr) : repo.findByStatus(status, pr);
    }

    // ============ Internals ============
    private DepositRequest lockPending(Long id) {
        DepositRequest d = repo.findByIdForUpdate(id)
                .orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Deposit not found"));
        if (d.getStatus() != Status.PENDING) throw err(HttpStatus.CONFLICT, "ডিপোজিট আগেই প্রসেস হয়েছে");
        return d;
    }

    /** ব্যালেন্স যোগ + status APPROVED (কলারকে অবশ্যই transaction-এর ভেতরে থাকতে হবে) */
    private void approveInternal(DepositRequest d, Long adminId, boolean auto) {
        User u = em.find(User.class, d.getUser().getId(), LockModeType.PESSIMISTIC_WRITE);
        u.addToDepositBalance(d.getAmount());

        d.setStatus(Status.APPROVED);
        d.setAutoApproved(auto);
        d.setProcessedById(adminId);
        d.setProcessedAt(LocalDateTime.now());
        repo.save(d);

        onDepositApproved(u, d);

        notifyAfterCommit(u.getGameId(), "আপনার " + d.getAmount() + " টাকার ডিপোজিট অ্যাপ্রুভ হয়েছে!");
        // কমিট হওয়ার পর ওয়ালেটের নতুন ব্যালেন্স রিয়েলটাইমে পাঠানো
        refreshProfileAfterCommit(u.getGameId());
    }

    /**
     * TODO: ডিপোজিট অ্যাপ্রুভ হলে যা যা হবে সেই বোনাস লজিক এখানে বসবে
     * (first-deposit বোনাস, রেফারারের PENDING বোনাস COMPLETED করা ইত্যাদি)।
     * এই মেথড approveInternal-এর transaction-এর ভেতরেই চলে।
     */
    private void onDepositApproved(User user, DepositRequest deposit) {
        // এখনো কোনো বোনাস নেই
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

    private void notifyAfterCommit(String gameId, String msg) {
        afterCommit(() -> {
            try {
                ws.convertAndSend("/topic/notifications/" + gameId,
                        new NotificationDto("Deposit Update", msg, LocalDateTime.now().toString()));
            } catch (Exception ignored) { /* নোটিফিকেশন ফেল হলেও ডিপোজিট নষ্ট হবে না */ }
        });
    }

    private void refreshProfileAfterCommit(String gameId) {
        afterCommit(() -> {
            try {
                userService.notifyUserUpdate(gameId);
            } catch (Exception ignored) { /* রিয়েলটাইম ফেল হলেও ডিপোজিট নষ্ট হবে না */ }
        });
    }

    private static BigDecimal validateAmount(BigDecimal a) {
        if (a == null) throw err(HttpStatus.BAD_REQUEST, "Amount দিন");
        a = a.setScale(2, RoundingMode.HALF_UP);
        if (a.compareTo(MIN) < 0) throw err(HttpStatus.BAD_REQUEST, "সর্বনিম্ন ডিপোজিট " + MIN + " টাকা");
        if (a.compareTo(MAX) > 0) throw err(HttpStatus.BAD_REQUEST, "সর্বোচ্চ ডিপোজিট " + MAX + " টাকা");
        return a;
    }

    private String newServerTxnId() {
        for (int i = 0; i < 30; i++) {
            String id = "DEP" + String.format("%07d", RND.nextInt(10_000_000));
            if (!repo.existsByTransactionId(id)) return id;
        }
        throw err(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction ID তৈরি করা যায়নি");
    }

    private static ResponseStatusException err(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }
}
