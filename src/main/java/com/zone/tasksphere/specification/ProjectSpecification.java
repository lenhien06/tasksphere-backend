package com.zone.tasksphere.specification;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.ProjectView;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProjectSpecification {

    public static Specification<Project> filterProjects(
            String search,
            ProjectStatus status,
            ProjectVisibility visibility,
            UUID userId,
            boolean isAdmin) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Full-text search theo name (Case-insensitive)
            if (StringUtils.hasText(search)) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.trim().toLowerCase() + "%"));
            }

            // 2. Filter theo status
            if (status != null) {
                if (status == ProjectStatus.ARCHIVED) {
                    // FR-13: archived projects are soft-deleted (deletedAt != null)
                    predicates.add(cb.isNotNull(root.get("deletedAt")));
                } else {
                    predicates.add(cb.equal(root.get("status"), status));
                    predicates.add(cb.isNull(root.get("deletedAt")));
                }
            } else {
                // Mặc định: Trả về ACTIVE (loại trừ COMPLETED và ARCHIVED)
                predicates.add(cb.equal(root.get("status"), ProjectStatus.ACTIVE));
                predicates.add(cb.isNull(root.get("deletedAt")));
            }

            // 3. Filter theo visibility
            if (visibility != null) {
                predicates.add(cb.equal(root.get("visibility"), visibility));
            }

            // 4. Phân quyền hiển thị
            // - Admin: thấy tất cả theo filter
            // - Guest: không thấy public project trong danh sách chung
            // - Logged user: chỉ thấy project mình sở hữu/tham gia
            //   hoặc public project mà họ đã truy cập bằng shared link trước đó
            if (!isAdmin) {
                if (userId == null) {
                    predicates.add(cb.disjunction());
                } else {
                    Predicate isOwner = cb.equal(root.get("owner").get("id"), userId);
                    Join<Project, ProjectMember> membersJoin = root.join("members", JoinType.LEFT);
                    Predicate isMember = cb.equal(membersJoin.get("user").get("id"), userId);
                    Join<Project, ProjectView> viewsJoin = root.join("views", JoinType.LEFT);
                    Predicate isVisitedPublic = cb.and(
                            cb.equal(root.get("visibility"), ProjectVisibility.PUBLIC),
                            cb.equal(viewsJoin.get("user").get("id"), userId)
                    );

                    predicates.add(cb.or(isOwner, isMember, isVisitedPublic));
                    query.distinct(true);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
