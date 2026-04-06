package com.zone.tasksphere.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserProfileResponse {

    private String id;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String jobTitle;
    private String bio;
    private List<String> skillTags;
    private int workCapacityHours;
    private List<ParticipatedProjectDto> participatedProjects;

    @Data
    @Builder
    public static class ParticipatedProjectDto {
        private String id;
        private String name;
        private String description;
        private String visibility;
        private String role;
    }
}
