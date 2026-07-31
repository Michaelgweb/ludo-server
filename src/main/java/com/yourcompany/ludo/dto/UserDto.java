package com.yourcompany.ludo.dto;


import java.math.BigDecimal;
import java.time.LocalDateTime;


public class UserDto {


    // ================= USER INFO =================


    private Long id;

    private String mobile;

    private String password;

    private String gameId;


    private BigDecimal balance;

    private BigDecimal depositBalance;

    private BigDecimal withdrawBalance;


    private String role;


    private String avatarUrl;


    private String displayName;


    private BigDecimal lifetimeEarnings;



    // ================= REFERRAL =================


    private String referralCode;

    private String referredBy;



    // ================= BONUS =================


    private boolean signupBonusClaimed;



    // ==================================================
    // DEVICE SECURITY INFORMATION
    // ==================================================


    private String deviceId;


    private String deviceFingerprint;


    private String deviceHash;


    private String deviceName;


    private String deviceModel;



    // ================= OS INFO =================


    private String osName;


    private String osVersion;



    // ================= BROWSER =================


    private String browser;


    private String userAgent;



    // ================= NETWORK =================


    private String ipAddress;


    private String lastLoginIp;


    private String isp;


    private String networkType;



    // ================= LOCATION =================


    private String country;


    private String city;


    private Double latitude;


    private Double longitude;



    // ================= HARDWARE =================


    private String cpu;


    private String ram;


    private String screenResolution;


    private String timezone;


    private String language;



    // ================= SECURITY =================


    private Boolean vpnDetected;


    private Boolean proxyDetected;


    private Boolean multiAccountDetected;



    private Integer riskScore;


    private String riskLevel;



    // ================= LOGIN SECURITY =================


    private LocalDateTime lastLoginTime;


    private Integer loginCount;


    private Integer failedLoginAttempt;


    private LocalDateTime lastFailedLogin;



    private String lastLoginDeviceId;


    private String lastLoginUserAgent;



    // ================= ACCOUNT STATUS =================


    private Boolean blocked;


    private Boolean phoneVerified;


    private Integer totalDevices;



    private Integer maxAllowedDevices = 3;





    // ==================================================
    // DEFAULT CONSTRUCTOR
    // ==================================================


    public UserDto(){


        this.balance =
                BigDecimal.ZERO;

        this.depositBalance =
                BigDecimal.ZERO;

        this.withdrawBalance =
                BigDecimal.ZERO;


        this.lifetimeEarnings =
                BigDecimal.ZERO;


        this.signupBonusClaimed =
                false;



        this.loginCount =
                0;



        this.failedLoginAttempt =
                0;



        this.blocked =
                false;



        this.phoneVerified =
                false;



        this.totalDevices =
                0;



        this.riskScore =
                0;



        this.riskLevel =
                "LOW";



        this.vpnDetected =
                false;



        this.proxyDetected =
                false;



        this.multiAccountDetected =
                false;


    }
    // ==================================================
// FULL CONSTRUCTOR
// ==================================================

public UserDto(

        Long id,

        String mobile,

        String password,

        String gameId,

        BigDecimal balance,

        BigDecimal depositBalance,

        BigDecimal withdrawBalance,

        String role,

        String avatarUrl,

        String displayName,

        BigDecimal lifetimeEarnings,


        String referralCode,

        String referredBy,


        boolean signupBonusClaimed,


        String deviceId,

        String deviceFingerprint,

        String deviceHash,

        String deviceName,

        String deviceModel,


        String osName,

        String osVersion,


        String browser,

        String userAgent,


        String ipAddress,

        String lastLoginIp,


        String isp,

        String networkType,


        String country,

        String city,


        Double latitude,

        Double longitude,


        String cpu,

        String ram,

        String screenResolution,

        String timezone,

        String language,


        Boolean vpnDetected,

        Boolean proxyDetected,

        Boolean multiAccountDetected,


        Integer riskScore,

        String riskLevel,


        LocalDateTime lastLoginTime,


        Integer loginCount,

        Integer failedLoginAttempt,


        LocalDateTime lastFailedLogin,


        String lastLoginDeviceId,

        String lastLoginUserAgent,


        Boolean blocked,

        Boolean phoneVerified,


        Integer totalDevices,


        Integer maxAllowedDevices


){



    this.id = id;


    this.mobile = mobile;


    this.password = password;


    this.gameId = gameId;



    this.balance =
            balance != null
            ? balance
            : BigDecimal.ZERO;

    this.depositBalance =
            depositBalance != null
            ? depositBalance
            : BigDecimal.ZERO;

    this.withdrawBalance =
            withdrawBalance != null
            ? withdrawBalance
            : BigDecimal.ZERO;



    this.role = role;



    this.avatarUrl = avatarUrl;


    this.displayName = displayName;




    this.lifetimeEarnings =
            lifetimeEarnings != null
            ? lifetimeEarnings
            : BigDecimal.ZERO;




    this.referralCode = referralCode;


    this.referredBy = referredBy;



    this.signupBonusClaimed =
            signupBonusClaimed;




    // ================= DEVICE =================


    this.deviceId = deviceId;


    this.deviceFingerprint =
            deviceFingerprint;


    this.deviceHash =
            deviceHash;


    this.deviceName =
            deviceName;


    this.deviceModel =
            deviceModel;




    // ================= OS =================


    this.osName =
            osName;


    this.osVersion =
            osVersion;




    // ================= BROWSER =================


    this.browser =
            browser;


    this.userAgent =
            userAgent;




    // ================= NETWORK =================


    this.ipAddress =
            ipAddress;


    this.lastLoginIp =
            lastLoginIp;



    this.isp =
            isp;


    this.networkType =
            networkType;




    // ================= LOCATION =================


    this.country =
            country;


    this.city =
            city;



    this.latitude =
            latitude;


    this.longitude =
            longitude;




    // ================= HARDWARE =================


    this.cpu =
            cpu;


    this.ram =
            ram;


    this.screenResolution =
            screenResolution;


    this.timezone =
            timezone;


    this.language =
            language;




    // ================= SECURITY =================


    this.vpnDetected =
            vpnDetected != null
            ? vpnDetected
            : false;



    this.proxyDetected =
            proxyDetected != null
            ? proxyDetected
            : false;



    this.multiAccountDetected =
            multiAccountDetected != null
            ? multiAccountDetected
            : false;




    this.riskScore =
            riskScore != null
            ? riskScore
            : 0;



    this.riskLevel =
            riskLevel != null
            ? riskLevel
            : "LOW";





    // ================= LOGIN =================


    this.lastLoginTime =
            lastLoginTime;


    this.loginCount =
            loginCount != null
            ? loginCount
            : 0;



    this.failedLoginAttempt =
            failedLoginAttempt != null
            ? failedLoginAttempt
            : 0;



    this.lastFailedLogin =
            lastFailedLogin;



    this.lastLoginDeviceId =
            lastLoginDeviceId;



    this.lastLoginUserAgent =
            lastLoginUserAgent;





    // ================= STATUS =================


    this.blocked =
            blocked != null
            ? blocked
            : false;




    this.phoneVerified =
            phoneVerified != null
            ? phoneVerified
            : false;




    this.totalDevices =
            totalDevices != null
            ? totalDevices
            : 0;



    this.maxAllowedDevices =
            maxAllowedDevices != null
            ? maxAllowedDevices
            : 3;


}
// ==================================================
// BASIC GETTER SETTER
// ==================================================


public Long getId(){
    return id;
}


public void setId(Long id){
    this.id=id;
}



public String getMobile(){
    return mobile;
}


public void setMobile(String mobile){
    this.mobile=mobile;
}



public String getPassword(){
    return password;
}


public void setPassword(String password){
    this.password=password;
}



public String getGameId(){
    return gameId;
}


public void setGameId(String gameId){
    this.gameId=gameId;
}



public BigDecimal getBalance(){

    return balance != null
            ? balance
            : BigDecimal.ZERO;

}


public void setBalance(BigDecimal balance){

    this.balance =
            balance != null
            ? balance
            : BigDecimal.ZERO;

}

public BigDecimal getDepositBalance(){
    return depositBalance != null
            ? depositBalance
            : BigDecimal.ZERO;
}

public void setDepositBalance(BigDecimal depositBalance){
    this.depositBalance = depositBalance != null
            ? depositBalance
            : BigDecimal.ZERO;
}

public BigDecimal getWithdrawBalance(){
    return withdrawBalance != null
            ? withdrawBalance
            : BigDecimal.ZERO;
}

public void setWithdrawBalance(BigDecimal withdrawBalance){
    this.withdrawBalance = withdrawBalance != null
            ? withdrawBalance
            : BigDecimal.ZERO;
}



public String getRole(){

    return role;

}


public void setRole(String role){

    this.role=role;

}



public String getAvatarUrl(){

    return avatarUrl;

}


public void setAvatarUrl(String avatarUrl){

    this.avatarUrl=avatarUrl;

}



public String getDisplayName(){

    return displayName;

}


public void setDisplayName(String displayName){

    this.displayName=displayName;

}



public BigDecimal getLifetimeEarnings(){

    return lifetimeEarnings;

}


public void setLifetimeEarnings(BigDecimal value){

    this.lifetimeEarnings =
            value != null
            ? value
            : BigDecimal.ZERO;

}



// ==================================================
// REFERRAL
// ==================================================


public String getReferralCode(){

    return referralCode;

}


public void setReferralCode(String referralCode){

    this.referralCode=referralCode;

}



public String getReferredBy(){

    return referredBy;

}


public void setReferredBy(String referredBy){

    this.referredBy=referredBy;

}



// ==================================================
// BONUS
// ==================================================


public boolean isSignupBonusClaimed(){

    return signupBonusClaimed;

}


public void setSignupBonusClaimed(boolean value){

    this.signupBonusClaimed=value;

}



// ==================================================
// DEVICE SECURITY GETTER SETTER
// ==================================================


public String getDeviceId(){

    return deviceId;

}


public void setDeviceId(String value){

    this.deviceId=value;

}



public String getDeviceFingerprint(){

    return deviceFingerprint;

}


public void setDeviceFingerprint(String value){

    this.deviceFingerprint=value;

}



public String getDeviceHash(){

    return deviceHash;

}


public void setDeviceHash(String value){

    this.deviceHash=value;

}



public String getDeviceName(){

    return deviceName;

}


public void setDeviceName(String value){

    this.deviceName=value;

}



public String getDeviceModel(){

    return deviceModel;

}


public void setDeviceModel(String value){

    this.deviceModel=value;

}



public String getOsName(){

    return osName;

}


public void setOsName(String value){

    this.osName=value;

}



public String getOsVersion(){

    return osVersion;

}


public void setOsVersion(String value){

    this.osVersion=value;

}



public String getBrowser(){

    return browser;

}


public void setBrowser(String value){

    this.browser=value;

}



public String getUserAgent(){

    return userAgent;

}


public void setUserAgent(String value){

    this.userAgent=value;

}



// ==================================================
// NETWORK
// ==================================================


public String getIpAddress(){

    return ipAddress;

}


public void setIpAddress(String value){

    this.ipAddress=value;

}



public String getLastLoginIp(){

    return lastLoginIp;

}


public void setLastLoginIp(String value){

    this.lastLoginIp=value;

}



public String getIsp(){

    return isp;

}


public void setIsp(String value){

    this.isp=value;

}



public String getNetworkType(){

    return networkType;

}


public void setNetworkType(String value){

    this.networkType=value;

}



// ==================================================
// LOCATION
// ==================================================


public String getCountry(){

    return country;

}


public void setCountry(String value){

    this.country=value;

}



public String getCity(){

    return city;

}


public void setCity(String value){

    this.city=value;

}



public Double getLatitude(){

    return latitude;

}


public void setLatitude(Double value){

    this.latitude=value;

}



public Double getLongitude(){

    return longitude;

}


public void setLongitude(Double value){

    this.longitude=value;

}



// ==================================================
// SECURITY
// ==================================================


public Boolean getVpnDetected(){

    return vpnDetected;

}


public void setVpnDetected(Boolean value){

    this.vpnDetected=value;

}



public Boolean getProxyDetected(){

    return proxyDetected;

}


public void setProxyDetected(Boolean value){

    this.proxyDetected=value;

}



public Boolean getMultiAccountDetected(){

    return multiAccountDetected;

}


public void setMultiAccountDetected(Boolean value){

    this.multiAccountDetected=value;

}



public Integer getRiskScore(){

    return riskScore;

}


public void setRiskScore(Integer value){

    this.riskScore=value;

}



public String getRiskLevel(){

    return riskLevel;

}


public void setRiskLevel(String value){

    this.riskLevel=value;

}



// ==================================================
// LOGIN
// ==================================================


public LocalDateTime getLastLoginTime(){

    return lastLoginTime;

}


public void setLastLoginTime(LocalDateTime value){

    this.lastLoginTime=value;

}



public Integer getLoginCount(){

    return loginCount;

}


public void setLoginCount(Integer value){

    this.loginCount=value;

}



public Integer getFailedLoginAttempt(){

    return failedLoginAttempt;

}


public void setFailedLoginAttempt(Integer value){

    this.failedLoginAttempt=value;

}



// ==================================================
// STATUS
// ==================================================


public Boolean getBlocked(){

    return blocked;

}


public void setBlocked(Boolean value){

    this.blocked=value;

}



public Boolean getPhoneVerified(){

    return phoneVerified;

}


public void setPhoneVerified(Boolean value){

    this.phoneVerified=value;

}



public Integer getTotalDevices(){

    return totalDevices;

}


public void setTotalDevices(Integer value){

    this.totalDevices=value;

}

public static class Builder{


    private UserDto dto = new UserDto();



    public Builder id(Long value){
        dto.id=value;
        return this;
    }



    public Builder mobile(String value){
        dto.mobile=value;
        return this;
    }



    public Builder gameId(String value){
        dto.gameId=value;
        return this;
    }



    public Builder balance(BigDecimal value){
        dto.balance=value;
        return this;
    }

    public Builder depositBalance(BigDecimal value){
        dto.depositBalance=value;
        return this;
    }

    public Builder withdrawBalance(BigDecimal value){
        dto.withdrawBalance=value;
        return this;
    }



    public Builder role(String value){
        dto.role=value;
        return this;
    }

    public Builder avatarUrl(String value){
        dto.avatarUrl=value;
        return this;
    }

    public Builder displayName(String value){
        dto.displayName=value;
        return this;
    }

    public Builder lifetimeEarnings(BigDecimal value){
        dto.lifetimeEarnings=value;
        return this;
    }

    public Builder referralCode(String value){
        dto.referralCode=value;
        return this;
    }



    public Builder deviceId(String value){
        dto.deviceId=value;
        return this;
    }



    public Builder deviceFingerprint(String value){
        dto.deviceFingerprint=value;
        return this;
    }



    public Builder ipAddress(String value){
        dto.ipAddress=value;
        return this;
    }



    public Builder country(String value){
        dto.country=value;
        return this;
    }



    public Builder city(String value){
        dto.city=value;
        return this;
    }



    public Builder riskScore(Integer value){
        dto.riskScore=value;
        return this;
    }



    public Builder riskLevel(String value){
        dto.riskLevel=value;
        return this;
    }



    public UserDto build(){

        return dto;

    }


}





@Override
public String toString(){


return "UserDto{" +

"id=" + id +

", mobile='" + mobile + '\'' +

", gameId='" + gameId + '\'' +

", balance=" + balance +

", depositBalance=" + depositBalance +

", withdrawBalance=" + withdrawBalance +

", deviceId='" + deviceId + '\'' +

", fingerprint='" + deviceFingerprint + '\'' +

", ipAddress='" + ipAddress + '\'' +

", country='" + country + '\'' +

", city='" + city + '\'' +

", riskScore=" + riskScore +

", riskLevel='" + riskLevel + '\'' +

", vpnDetected=" + vpnDetected +

", proxyDetected=" + proxyDetected +

", blocked=" + blocked +

'}';


}
}
