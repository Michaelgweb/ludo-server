package com.yourcompany.ludo.repository;

import com.yourcompany.ludo.model.AppVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {
    Optional<AppVersion> findTopByOrderByVersionCodeDesc();
}
