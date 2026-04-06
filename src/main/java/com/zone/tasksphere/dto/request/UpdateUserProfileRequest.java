package com.zone.tasksphere.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserProfileRequest {

    @Size(min = 2, max = 100, message = "Tên phải từ 2 đến 100 ký tự")
    private String fullName;

    @Size(max = 100, message = "Chức danh tối đa 100 ký tự")
    private String jobTitle;

    @Size(max = 500, message = "Giới thiệu tối đa 500 ký tự")
    private String bio;

    @Min(value = 1, message = "Số giờ/tuần tối thiểu là 1")
    @Max(value = 168, message = "Số giờ/tuần tối đa là 168")
    private Integer workCapacityHours;
}
