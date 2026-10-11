package com.yourcompany.ludo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_versions")
public class AppVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String versionName;
    private int versionCode;
    private boolean forceUpdate;
    private String apkUrl;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public int getVersionCode() { return versionCode; }
    public void setVersionCode(int versionCode) { this.versionCode = versionCode; }

    public boolean isForceUpdate() { return forceUpdate; }
    public void setForceUpdate(boolean forceUpdate) { this.forceUpdate = forceUpdate; }

    public String getApkUrl() { return apkUrl; }
    public void setApkUrl(String apkUrl) { this.apkUrl = apkUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
