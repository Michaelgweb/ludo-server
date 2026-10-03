package com.yourcompany.ludo.service;

import com.yourcompany.ludo.model.AdminPaymentAccount;
import com.yourcompany.ludo.repository.AdminPaymentAccountRepository;
import com.yourcompany.ludo.util.PaymentRules;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentRotationService {

    public record AccountDto(Long id, String method, String number, boolean active, long assignCount) {}

    private final AdminPaymentAccountRepository repository;

    public PaymentRotationService(AdminPaymentAccountRepository repository) {
        this.repository = repository;
    }

    private static AccountDto toDto(AdminPaymentAccount a) {
        return new AccountDto(a.getId(), a.getMethod(), a.getNumber(), a.isActive(), a.getAssignCount());
    }

    // ---------- ইউজার ডিপোজিট: পরের নম্বর (rotation) ----------
    @Transactional
    public AccountDto assignNext(String method) {
        String m = PaymentRules.normalizeMethod(method);
        List<AdminPaymentAccount> list = repository.findNextForUpdate(m, PageRequest.of(0, 1));
        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "এই মেথডে কোনো active নম্বর নেই");
        }
        AdminPaymentAccount a = list.get(0);
        a.setAssignCount(a.getAssignCount() + 1);
        a.setLastAssignedAt(LocalDateTime.now());
        return toDto(repository.save(a));
    }

    // ---------- অ্যাডমিন CRUD (আনলিমিটেড) ----------
    @Transactional(readOnly = true)
    public List<AccountDto> listAll() {
        return repository.findAllByOrderByMethodAscIdAsc().stream().map(PaymentRotationService::toDto).toList();
    }

    @Transactional
    public AccountDto add(String method, String number) {
        String m = PaymentRules.normalizeMethod(method);
        String n = PaymentRules.normalizeNumber(number);
        if (repository.existsByMethodAndNumber(m, n)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "এই নম্বর আগেই আছে");
        }
        AdminPaymentAccount a = new AdminPaymentAccount();
        a.setMethod(m);
        a.setNumber(n);
        return toDto(repository.save(a));
    }

    @Transactional
    public AccountDto setActive(Long id, boolean active) {
        AdminPaymentAccount a = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        a.setActive(active);
        return toDto(repository.save(a));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        repository.deleteById(id);
    }
}
