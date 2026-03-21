package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.CustomFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomFieldValueRepository extends JpaRepository<CustomFieldValue, UUID> {

    List<CustomFieldValue> findByTaskId(UUID taskId);

    Optional<CustomFieldValue> findByTaskIdAndCustomFieldId(UUID taskId, UUID customFieldId);

    boolean existsByCustomFieldId(UUID customFieldId);

    @Modifying
    @Query("DELETE FROM CustomFieldValue v WHERE v.customField.id = :fieldId")
    void deleteByCustomFieldId(@Param("fieldId") UUID fieldId);
}
