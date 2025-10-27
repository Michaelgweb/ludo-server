package com.yourcompany.ludo.dto;

public class OtpVerifyRequest {
    private String password;
    private String mobile;
    private String otp;

    // নতুন ফিল্ড যোগ
    private String referralCode;

    // ===== Getters =====
    public String getMobile() { return mobile; }
    public String getPassword() { return password; }
    public String getOtp() { return otp; }
    public String getReferralCode() { return referralCode; }

    // ===== Setters =====
    public void setMobile(String mobile) { this.mobile = mobile; }
    public void setPassword(String password) { this.password = password; }
    public void setOtp(String otp) { this.otp = otp; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
}