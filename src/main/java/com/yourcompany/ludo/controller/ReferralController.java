package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.ReferralSummaryDto;
import com.yourcompany.ludo.model.User;
import com.yourcompany.ludo.service.ReferralService;
import com.yourcompany.ludo.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
public class ReferralController {

    @Autowired
    private ReferralService referralService;

    @Autowired
    private UserService userService;

    private User currentUser(Authentication authentication) {
        if (authentication == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        return userService.findByGameId(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    /** ইউজার: শুধু নিজের রেফারেল (কত জন, পেন্ডিং, কমপ্লিট)। gameId প্যারামিটার নেই, তাই অন্যেরটা দেখা সম্ভব নয়। */
    @GetMapping("/api/user/referrals")
    public ResponseEntity<ReferralSummaryDto> myReferrals(Authentication authentication) {
        User me = currentUser(authentication);
        return ResponseEntity.ok(referralService.getMyReferrals(me.getGameId()));
    }

    /** অ্যাডমিন: সবার রেফারেল। ?status=PENDING বা COMPLETED দিয়ে ফিল্টার করা যায়। */
    @GetMapping("/api/admin/referrals")
    public ResponseEntity<ReferralSummaryDto> allReferrals(
            @RequestParam(required = false) String status,
            Authentication authentication) {

        User me = currentUser(authentication);
        if (me.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }

        String st = null;
        if (status != null && !status.isBlank()) {
            st = status.trim().toUpperCase(Locale.ROOT);
            if (!st.equals("PENDING") && !st.equals("COMPLETED")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be PENDING or COMPLETED");
            }
        }
        return ResponseEntity.ok(referralService.getAllReferrals(st));
    }
}
