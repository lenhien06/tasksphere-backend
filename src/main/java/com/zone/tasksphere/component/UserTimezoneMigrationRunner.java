package com.zone.tasksphere.component;

import com.zone.tasksphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(3)
public class UserTimezoneMigrationRunner implements ApplicationRunner {

    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = userRepository.migrateLegacyDefaultTimezone(DEFAULT_TIMEZONE);

        if (updated > 0) {
            log.info("[UserTimezoneMigration] Updated {} user(s) to default timezone {}", updated, DEFAULT_TIMEZONE);
        } else {
            log.info("[UserTimezoneMigration] No legacy user timezone to migrate.");
        }
    }
}
