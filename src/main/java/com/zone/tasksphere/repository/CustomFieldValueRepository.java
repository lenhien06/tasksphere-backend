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

    /**
     * {@code hasValues} trên definition: chỉ true khi có ít nhất một bản ghi với giá trị thực sự
     * (một trong các cột typed khác null; text phải non-blank sau trim).
     * Không dùng {@link #existsByCustomFieldId} — nó đếm cả hàng “rỗng” (mọi cột null).
     */
    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM CustomFieldValue v
            WHERE v.customField.id = :fieldId
              AND (
                   (v.textValue IS NOT NULL AND TRIM(v.textValue) <> '')
                OR v.numberValue IS NOT NULL
                OR v.dateValue IS NOT NULL
                OR v.booleanValue IS NOT NULL
              )
            """)
    boolean existsNonEmptyValueByCustomFieldId(@Param("fieldId") UUID fieldId);

    @Modifying
    @Query("DELETE FROM CustomFieldValue v WHERE v.customField.id = :fieldId")
    void deleteByCustomFieldId(@Param("fieldId") UUID fieldId);
}
