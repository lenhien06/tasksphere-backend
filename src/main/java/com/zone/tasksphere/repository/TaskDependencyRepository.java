package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.TaskDependency;
import com.zone.tasksphere.entity.enums.DependencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    /**
     * Tìm tất cả task mà taskId phụ thuộc vào (taskId bị block bởi các task này).
     * blockingTask = task phải xong trước; blockedTask = task đang bị block.
     */
    @Query("SELECT d.blockingTask.id FROM TaskDependency d WHERE d.blockedTask.id = :taskId")
    List<UUID> findDependsOnIdsByTaskId(@Param("taskId") UUID taskId);

    /**
     * Tìm tất cả task đang bị taskId block (các task này phụ thuộc vào taskId).
     */
    @Query("SELECT d.blockedTask.id FROM TaskDependency d WHERE d.blockingTask.id = :taskId")
    List<UUID> findDependentTaskIdsByTaskId(@Param("taskId") UUID taskId);

    /** Full list cho GET endpoint — blockedBy (task này bị block bởi ai) */
    @Query("SELECT d FROM TaskDependency d JOIN FETCH d.blockingTask WHERE d.blockedTask.id = :taskId")
    List<TaskDependency> findBlockedByWithTask(@Param("taskId") UUID taskId);

    /** Full list cho GET endpoint — blocking (task này đang block ai) */
    @Query("SELECT d FROM TaskDependency d JOIN FETCH d.blockedTask WHERE d.blockingTask.id = :taskId")
    List<TaskDependency> findBlockingWithTask(@Param("taskId") UUID taskId);

    /** Tìm dependency record theo id và taskId (để validate khi DELETE) */
    Optional<TaskDependency> findByIdAndBlockedTaskId(UUID id, UUID blockedTaskId);

    /** Kiểm tra dependency đã tồn tại chưa */
    boolean existsByBlockingTaskIdAndBlockedTaskId(UUID blockingTaskId, UUID blockedTaskId);

    /** GET /links — tất cả links từ góc nhìn của sourceTask (blockingTask = sourceTask) */
    @Query("SELECT d FROM TaskDependency d JOIN FETCH d.blockedTask WHERE d.blockingTask.id = :taskId")
    List<TaskDependency> findLinksBySourceTaskId(@Param("taskId") UUID taskId);

    /** Tìm inverse link để xóa cùng với link gốc */
    Optional<TaskDependency> findByBlockingTaskIdAndBlockedTaskIdAndLinkType(
            UUID blockingTaskId, UUID blockedTaskId, DependencyType linkType);

    /** Xóa tất cả links liên quan đến một task (cho soft delete task) */
    @Query("SELECT d FROM TaskDependency d WHERE d.blockingTask.id = :taskId OR d.blockedTask.id = :taskId")
    List<TaskDependency> findAllByTaskId(@Param("taskId") UUID taskId);
}
