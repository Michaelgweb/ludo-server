package com.yourcompany.ludo.controller;

import com.yourcompany.ludo.dto.OtpRequest;
import com.yourcompany.ludo.dto.OtpVerifyRequest;
import com.yourcompany.ludo.dto.OtpPasswordResetRequest; // ✅ Import this!
import com.yourcompany.ludo.service.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/otp")
public class OtpController {

    @Autowired
    private OtpService otpService;

    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestBody OtpRequest request) {
        return otpService.sendOtpToPhone(request.getMobile());
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyRequest request) {
        return otpService.verifyOtpAndRegister(request);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody OtpPasswordResetRequest request) {
        return otpService.resetPasswordWithOtp(request);
    }
}
