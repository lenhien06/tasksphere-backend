package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.EmailOtp;
import com.zone.tasksphere.entity.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {

    /** Find the latest valid (unused, unexpired) OTP for a given email and purpose. */
    @Query("SELECT o FROM EmailOtp o WHERE o.email = :email AND o.purpose = :purpose " +
           "AND o.verifiedAt IS NULL AND o.expiredAt > CURRENT_TIMESTAMP " +
           "ORDER BY o.createdAt DESC LIMIT 1")
    Optional<EmailOtp> findLatestValid(String email, OtpPurpose purpose);

    /** Delete all unverified OTPs for the same email and purpose (before creating a new one). */
    @Modifying
    @Query("DELETE FROM EmailOtp o WHERE o.email = :email AND o.purpose = :purpose AND o.verifiedAt IS NULL")
    void deleteUnverifiedByEmailAndPurpose(String email, OtpPurpose purpose);
}
