package com.yourcompany.ludo.dto;

public class OtpVerifyRequest {
    private String password;
    private String mobile;
    private String otp;

    public String getMobile() { return mobile; }
    public String getPassword() { return password; }
    public String getOtp() { return otp; }

    public void setMobile(String mobile) { this.mobile = mobile; }
    public void setPassword(String password) { this.password = password; }
    public void setOtp(String otp) { this.otp = otp; }
}
