package com.zone.tasksphere.specification;

import com.zone.tasksphere.entity.ActivityLog;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public class ActivityLogSpecification {

    public static Specification<ActivityLog> hasProjectId(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
    }

    public static Specification<ActivityLog> hasActorId(UUID actorId) {
        return (root, query, cb) -> actorId == null ? null : cb.equal(root.get("actor").get("id"), actorId);
    }

    public static Specification<ActivityLog> hasEntityType(EntityType entityType) {
        return (root, query, cb) -> entityType == null ? null : cb.equal(root.get("entityType"), entityType);
    }

    public static Specification<ActivityLog> hasAction(ActionType action) {
        return (root, query, cb) -> action == null ? null : cb.equal(root.get("action"), action);
    }

    public static Specification<ActivityLog> isBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from != null && to != null) return cb.between(root.get("createdAt"), from, to);
            if (from != null) return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            return cb.lessThanOrEqualTo(root.get("createdAt"), to);
        };
    }
}
