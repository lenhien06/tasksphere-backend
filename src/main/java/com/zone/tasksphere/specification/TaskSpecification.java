package com.zone.tasksphere.specification;

import com.zone.tasksphere.dto.request.TaskFilterParams;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TaskSpecification {

    /**
     * P4-BE-03: Backlog filter — chỉ lấy task không thuộc sprint nào
     */
    public static Specification<Task> isBacklog() {
        return (root, query, cb) -> cb.isNull(root.get("sprint"));
    }

    /**
     * Build Specification từ TaskFilterParams (dùng trong phân trang)
     */
    public static Specification<Task> buildFilter(TaskFilterParams params) {
        return buildFilter(params, true);
    }

    public static Specification<Task> buildFilter(TaskFilterParams params, boolean rootTasksOnly) {
        return (root, query, cb) -> {
            // LEFT JOIN FETCH sprint để tránh N+1 khi map sprintId/sprintName
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("sprint", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();

            // Bắt buộc thuộc project này
            if (params.getProjectId() != null) {
                predicates.add(cb.equal(root.get("project").get("id"), params.getProjectId()));
            }

            // Chỉ task gốc cho board/list mặc định; timeline/calendar có thể cần cả sub-task.
            if (rootTasksOnly) {
                predicates.add(cb.isNull(root.get("parentTask")));
            }

            // Tìm kiếm theo title hoặc taskCode
            if (params.getQ() != null && !params.getQ().isBlank()) {
                String pattern = "%" + params.getQ().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("taskCode")), pattern)
                ));
            }

            if (params.getStatus() != null) {
                predicates.add(cb.equal(root.get("taskStatus"), params.getStatus()));
            }

            if (params.getAssigneeId() != null) {
                try {
                    UUID assigneeUuid = UUID.fromString(params.getAssigneeId());
                    predicates.add(cb.equal(root.get("assignee").get("id"), assigneeUuid));
                } catch (IllegalArgumentException ignored) {
                    // "me" đã được resolve thành UUID thật ở service layer trước khi vào đây
                }
            }

            if (params.getSprintId() != null) {
                predicates.add(cb.equal(root.get("sprint").get("id"), params.getSprintId()));
            }

            if (params.getPriorities() != null && !params.getPriorities().isEmpty()) {
                predicates.add(root.get("priority").in(params.getPriorities()));
            } else if (params.getPriority() != null) {
                predicates.add(cb.equal(root.get("priority"), params.getPriority()));
            }

            if (params.getType() != null) {
                predicates.add(cb.equal(root.get("type"), params.getType()));
            }

            // Lọc task quá hạn
            if (Boolean.TRUE.equals(params.getOverdue())) {
                predicates.add(cb.and(
                    cb.isNotNull(root.get("dueDate")),
                    cb.lessThan(root.get("dueDate"), LocalDate.now()),
                    cb.notEqual(root.get("taskStatus"), TaskStatus.DONE),
                    cb.notEqual(root.get("taskStatus"), TaskStatus.CANCELLED)
                ));
            }

            // FR-15/FR-27: Lọc task sắp đến hạn (dueDate trong 7 ngày tới, status NOT DONE/CANCELLED)
            if (Boolean.TRUE.equals(params.getDueSoon())) {
                LocalDate today = LocalDate.now();
                LocalDate in7Days = today.plusDays(7);
                predicates.add(cb.isNotNull(root.get("dueDate")));
                predicates.add(cb.between(root.get("dueDate"), today, in7Days));
                predicates.add(cb.not(root.get("taskStatus").in(TaskStatus.DONE, TaskStatus.CANCELLED)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
