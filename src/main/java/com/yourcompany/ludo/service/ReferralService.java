package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.ReferralItemDto;
import com.yourcompany.ludo.dto.ReferralSummaryDto;
import com.yourcompany.ludo.model.BonusHistory;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.repository.BonusHistoryRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReferralService {

    /** UserServiceImpl.giveReferralBonus যে type দিয়ে রেকর্ড করে */
    private static final String TYPE = "REFERRER_PENDING";

    @Autowired
    private BonusHistoryRepository bonusHistoryRepository;

    @Autowired
    private UserService userService;

    /** ইউজার: শুধু নিজের রেফারেল */
    @Transactional(readOnly = true)
    public ReferralSummaryDto getMyReferrals(String gameId) {
        List<BonusHistory> rows =
                bonusHistoryRepository.findByUserGameIdAndTypeOrderByCreatedAtDesc(gameId, TYPE);
        return build(rows, rows, false);
    }

    /** অ্যাডমিন: সবার রেফারেল (status দিলে লিস্ট ফিল্টার হয়, মোট সংখ্যা সবসময় সবার) */
    @Transactional(readOnly = true)
    public ReferralSummaryDto getAllReferrals(String status) {
        List<BonusHistory> all = bonusHistoryRepository.findByTypeOrderByCreatedAtDesc(TYPE);
        List<BonusHistory> shown = all;
        if (status != null && !status.isBlank()) {
            shown = all.stream()
                    .filter(b -> status.equalsIgnoreCase(b.getStatus()))
                    .toList();
        }
        return build(all, shown, true);
    }

    // ---------------------------------------------------------------------

    private ReferralSummaryDto build(List<BonusHistory> all, List<BonusHistory> shown, boolean admin) {
        long pending = 0, completed = 0;
        BigDecimal pendingAmt = BigDecimal.ZERO, completedAmt = BigDecimal.ZERO;

        for (BonusHistory b : all) {
            BigDecimal a = b.getAmount() == null ? BigDecimal.ZERO : b.getAmount();
            if ("PENDING".equalsIgnoreCase(b.getStatus())) {
                pending++;
                pendingAmt = pendingAmt.add(a);
            } else if ("COMPLETED".equalsIgnoreCase(b.getStatus())) {
                completed++;
                completedAmt = completedAmt.add(a);
            }
        }

        Map<String, User> cache = new HashMap<>();
        List<ReferralItemDto> items = new ArrayList<>();

        for (BonusHistory b : shown) {
            User referred = lookup(cache, b.getSourceGameId());
            String referredName = referred == null ? null : referred.getDisplayName();

            if (admin) {
                User referrer = lookup(cache, b.getUserGameId());
                items.add(new ReferralItemDto(
                        b.getUserGameId(),
                        referrer == null ? null : referrer.getDisplayName(),
                        b.getSourceGameId(),
                        referredName,
                        referred == null ? null : referred.getMobile(),
                        b.getStatus(),
                        b.getAmount(),
                        b.getCreatedAt()));
            } else {
                items.add(new ReferralItemDto(
                        null,
                        null,
                        null,
                        referredName,
                        referred == null ? null : maskMobile(referred.getMobile()),
                        b.getStatus(),
                        b.getAmount(),
                        b.getCreatedAt()));
            }
        }

        return new ReferralSummaryDto(all.size(), pending, completed, pendingAmt, completedAmt, items);
    }

    private User lookup(Map<String, User> cache, String gameId) {
        if (gameId == null) return null;
        if (cache.containsKey(gameId)) return cache.get(gameId);
        User u = userService.findByGameId(gameId).orElse(null);
        cache.put(gameId, u);
        return u;
    }

    /** 8801712345678 -> 880*******78 */
    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 6) return "****";
        return mobile.substring(0, 3) + "*".repeat(mobile.length() - 5) + mobile.substring(mobile.length() - 2);
    }
}
