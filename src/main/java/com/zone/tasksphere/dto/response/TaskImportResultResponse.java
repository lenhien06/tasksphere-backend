package com.zone.tasksphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskImportResultResponse {
    private int totalRows;
    private int createdCount;
    private List<TaskImportErrorDto> errors;
}
