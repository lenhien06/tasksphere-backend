package com.zone.tasksphere;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.zone.tasksphere.service.UserService;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.TaskSphereApplication;
import com.zone.tasksphere.repository.UserRepository;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TaskSphereApplication.class)
@ActiveProfiles("dev")
public class ExportTest {
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    @Test
    public void testExport() {
        try {
            User user = userRepository.findAll().stream().findFirst().orElseThrow();
            System.out.println("USER ID: " + user.getId());
            userService.exportUserPerformanceCsv(user.getId());
            System.out.println("EXPORT SUCCESSFUL");
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
