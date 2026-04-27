package com.zone.tasksphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskImportErrorDto {
    private int row;
    private String column;
    private String message;
}
