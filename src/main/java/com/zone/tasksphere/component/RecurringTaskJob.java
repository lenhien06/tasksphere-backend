package com.zone.tasksphere.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.entity.RecurringTaskConfig;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.enums.RecurrenceStatus;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.repository.RecurringTaskConfigRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.utils.TaskCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * P6-BE-02: Scheduled job that generates recurring task instances daily at 00:05.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTaskJob {

    private final RecurringTaskConfigRepository recurringConfigRepository;
    private final TaskRepository taskRepository;
    private final TaskCodeGenerator taskCodeGenerator;
    private final ObjectMapper objectMapper;

    /**
     * Runs at 00:05 every day.
     * Finds all ACTIVE recurring configs with nextRunAt <= now and spawns new instances.
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void generateRecurringTasks() {
        Instant now = Instant.now();
        log.info("[RecurringTaskJob] Starting recurring task generation at {}", now);

        List<RecurringTaskConfig> dueConfigs = recurringConfigRepository.findActiveAndDue(now);
        log.info("[RecurringTaskJob] Found {} due recurring configs", dueConfigs.size());

        int generated = 0;
        int skipped = 0;

        for (RecurringTaskConfig rec : dueConfigs) {
            try {
                boolean created = processOne(rec, now);
                if (created) generated++;
                else skipped++;
            } catch (Exception e) {
                log.error("[RecurringTaskJob] Failed to process recurrence {}: {}", rec.getId(), e.getMessage(), e);
            }
        }

        log.info("[RecurringTaskJob] Completed. Generated={}, Skipped={}", generated, skipped);
    }

    /**
     * Process a single recurring config: validate limits, clone task, advance schedule.
     *
     * @return true if a new instance was created, false if the recurrence was completed/skipped.
     */
    private boolean processOne(RecurringTaskConfig rec, Instant now) {
        Task template = rec.getTask();

        // Check endDate
        if (rec.getEndDate() != null && LocalDate.now(ZoneOffset.UTC).isAfter(rec.getEndDate())) {
            rec.setStatus(RecurrenceStatus.COMPLETED);
            recurringConfigRepository.save(rec);
            log.info("[RecurringTaskJob] Recurrence {} completed (endDate passed)", rec.getId());
            return false;
        }

        // Check maxOccurrences
        if (rec.getOccurrenceCount() >= rec.getMaxOccurrences()) {
            rec.setStatus(RecurrenceStatus.COMPLETED);
            recurringConfigRepository.save(rec);
            log.info("[RecurringTaskJob] Recurrence {} completed (maxOccurrences={} reached)",
                    rec.getId(), rec.getMaxOccurrences());
            return false;
        }

        // Clone the template task
        String newTaskCode = taskCodeGenerator.generateTaskCode(template.getProject().getId());

        // Calculate dueDate offset from original if template had one
        LocalDate newDueDate = null;
        if (template.getDueDate() != null) {
            LocalDate templateStart = template.getStartDate() != null
                    ? template.getStartDate()
                    : LocalDate.ofInstant(template.getCreatedAt(), ZoneOffset.UTC);
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(templateStart, template.getDueDate());

            LocalDate instanceStartDate = LocalDate.ofInstant(rec.getNextRunAt(), ZoneOffset.UTC);
            newDueDate = instanceStartDate.plusDays(daysBetween);
        }

        Task instance = Task.builder()
                .taskCode(newTaskCode)
                .project(template.getProject())
                .statusColumn(template.getStatusColumn())
                .taskStatus(TaskStatus.TODO)
                .title(template.getTitle())
                .description(template.getDescription())
                .type(template.getType())
                .priority(template.getPriority())
                .assignee(template.getAssignee())
                .reporter(template.getReporter())
                .storyPoints(template.getStoryPoints())
                .estimatedHours(template.getEstimatedHours())
                .startDate(LocalDate.ofInstant(rec.getNextRunAt(), ZoneOffset.UTC))
                .dueDate(newDueDate)
                .depth(0)
                .taskPosition(0)
                .isRecurring(false)
                .parentRecurringTaskId(template.getId().toString())
                .build();

        taskRepository.save(instance);
        log.info("[RecurringTaskJob] Created instance {} (code={}) from template {}",
                instance.getId(), newTaskCode, template.getId());

        // Advance the recurrence schedule
        int newCount = rec.getOccurrenceCount() + 1;
        Instant nextRun = calculateNextRunAt(rec, rec.getNextRunAt());

        rec.setOccurrenceCount(newCount);
        rec.setLastGeneratedAt(now);

        if (nextRun == null) {
            // Could not compute next run (e.g. invalid cron) — pause the recurrence
            rec.setStatus(RecurrenceStatus.PAUSED);
            log.warn("[RecurringTaskJob] Could not compute next run for recurrence {} — paused.", rec.getId());
        } else {
            rec.setNextRunAt(nextRun);
            // Check again if we just hit the max
            if (newCount >= rec.getMaxOccurrences()) {
                rec.setStatus(RecurrenceStatus.COMPLETED);
                log.info("[RecurringTaskJob] Recurrence {} completed after {} occurrences", rec.getId(), newCount);
            }
        }

        recurringConfigRepository.save(rec);
        return true;
    }

    /**
     * Calculate the next scheduled run time based on frequency.
     */
    private Instant calculateNextRunAt(RecurringTaskConfig rec, Instant from) {
        LocalDateTime ldt = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        return switch (rec.getFrequency()) {
            case DAILY   -> ldt.plusDays(1).toInstant(ZoneOffset.UTC);
            case WEEKLY  -> ldt.plusWeeks(1).toInstant(ZoneOffset.UTC);
            case MONTHLY -> ldt.plusMonths(1).toInstant(ZoneOffset.UTC);
            case YEARLY  -> ldt.plusYears(1).toInstant(ZoneOffset.UTC);
            case CUSTOM  -> {
                String cronExpr = getFrequencyConfigValue(rec, "cronExpression");
                if (cronExpr == null || cronExpr.isBlank()) {
                    log.warn("[RecurringTaskJob] CUSTOM recurrence {} has no cronExpression", rec.getId());
                    yield null;
                }
                try {
                    CronExpression cron = CronExpression.parse(cronExpr);
                    LocalDateTime next = cron.next(ldt);
                    yield next != null ? next.toInstant(ZoneOffset.UTC) : null;
                } catch (Exception e) {
                    log.warn("[RecurringTaskJob] Invalid cron expression '{}' for recurrence {}: {}",
                            cronExpr, rec.getId(), e.getMessage());
                    yield null;
                }
            }
        };
    }

    private String getFrequencyConfigValue(RecurringTaskConfig rec, String key) {
        if (rec.getFrequencyConfig() == null) return null;
        try {
            Map<String, Object> config = objectMapper.readValue(
                    rec.getFrequencyConfig(), new TypeReference<Map<String, Object>>() {});
            Object val = config.get(key);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.warn("[RecurringTaskJob] Failed to parse frequencyConfig for recurrence {}: {}",
                    rec.getId(), e.getMessage());
            return null;
        }
    }
}
