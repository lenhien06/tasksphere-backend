package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.TaskImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface TaskImportService {
    byte[] generateTemplate() throws IOException;
    TaskImportResultResponse importTasks(UUID projectId, MultipartFile file, UUID currentUserId) throws IOException;
}
