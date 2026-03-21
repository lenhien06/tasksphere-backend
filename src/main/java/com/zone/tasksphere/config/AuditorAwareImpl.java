package com.zone.tasksphere.config;

import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Stub auditor — BaseEntity no longer uses @CreatedBy / @LastModifiedBy.
 * Kept for Spring Data JPA compatibility if needed in the future.
 */
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.empty();
    }
}
