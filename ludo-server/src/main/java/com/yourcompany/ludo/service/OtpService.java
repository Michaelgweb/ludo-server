package com.yourcompany.ludo.service;

import com.yourcompany.ludo.dto.OtpPasswordResetRequest;
import com.yourcompany.ludo.dto.OtpVerifyRequest;
import org.springframework.http.ResponseEntity;

public interface OtpService {
    ResponseEntity<?> sendOtpToPhone(String mobile);
    ResponseEntity<?> verifyOtpAndRegister(OtpVerifyRequest request);
    ResponseEntity<?> resetPasswordWithOtp(OtpPasswordResetRequest request);

}
