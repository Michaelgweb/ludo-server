package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.BonusHistoryDto;
import com.yourcompany.ludo.service.BonusHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bonus-history")
public class BonusHistoryController {

    @Autowired
    private BonusHistoryService bonusHistoryService;

    // 🔹 ইউজারের সব বোনাস হিস্টোরি
    @GetMapping("/my")
    public ResponseEntity<List<BonusHistoryDto>> getMyBonusHistory(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        String gameId = authentication.getName();
        return ResponseEntity.ok(bonusHistoryService.getUserBonusHistory(gameId));
    }

    // 🔹 শুধুমাত্র REFERRAL বোনাস হিস্টোরি (যেকোনো ইউজারের জন্য)
    @GetMapping("/my-referrals")
    public ResponseEntity<List<BonusHistoryDto>> getMyReferralHistory(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        String gameId = authentication.getName();
        return ResponseEntity.ok(bonusHistoryService.getUserReferralHistory(gameId));
    }

    // 🔹 অ্যাডমিনের জন্য: সকল রেফারাল হিস্ট্রি দেখা
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all-referrals")
    public ResponseEntity<List<BonusHistoryDto>> getAllReferralHistory() {
        return ResponseEntity.ok(bonusHistoryService.getAllReferralHistoryForAdmin());
    }
}
