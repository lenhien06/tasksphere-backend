package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.CustomField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomFieldRepository extends JpaRepository<CustomField, UUID> {

    List<CustomField> findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID projectId);

    List<CustomField> findByProjectIdAndIsHiddenFalseAndDeletedAtIsNullOrderBySortOrderAsc(UUID projectId);

    boolean existsByProjectIdAndNameAndDeletedAtIsNull(UUID projectId, String name);

    boolean existsByProjectIdAndNameAndIdNotAndDeletedAtIsNull(UUID projectId, String name, UUID excludeId);

    long countByProjectIdAndDeletedAtIsNull(UUID projectId);

    Optional<CustomField> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);
}
