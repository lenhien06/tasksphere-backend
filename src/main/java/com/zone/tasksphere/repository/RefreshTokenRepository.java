package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.RefreshToken;
import com.zone.tasksphere.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiredAt < :now")
    void deleteAllExpiredBefore(Instant now);
}
