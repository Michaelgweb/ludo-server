package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.BonusHistoryDto;
import com.yourcompany.ludo.model.BonusHistory;
import com.yourcompany.ludo.repository.BonusHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BonusHistoryService {

    @Autowired
    private BonusHistoryRepository bonusHistoryRepository;

    // ================= User Specific =================
    public List<BonusHistoryDto> getUserBonusHistory(String userGameId) {
        List<BonusHistory> histories = bonusHistoryRepository.findByUserGameIdOrderByCreatedAtDesc(userGameId);
        return histories.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ইউজারের সব referral related (PENDING + COMPLETED)
    public List<BonusHistoryDto> getUserReferralHistory(String userGameId) {
        List<String> types = List.of("REFERRAL", "REFERRER_PENDING");
        List<BonusHistory> histories = bonusHistoryRepository.findByUserGameIdAndTypeInOrderByCreatedAtDesc(userGameId, types);
        return histories.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ================= Admin Specific =================
    public List<BonusHistoryDto> getAllReferralHistoryForAdmin() {
        List<String> types = List.of("REFERRAL", "REFERRER_PENDING");
        List<BonusHistory> histories = bonusHistoryRepository.findByTypeInOrderByCreatedAtDesc(types);
        return histories.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<BonusHistoryDto> getAllBonusHistoryForAdmin() {
        List<BonusHistory> histories = bonusHistoryRepository.findAllByOrderByCreatedAtDesc();
        return histories.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ================= Helper =================
    private BonusHistoryDto toDto(BonusHistory h) {
        return new BonusHistoryDto(
                h.getType(),
                h.getAmount(),
                h.getSourceGameId(),
                h.getUserGameId(),
                h.getStatus(),
                h.getCreatedAt()
        );
    }
}
