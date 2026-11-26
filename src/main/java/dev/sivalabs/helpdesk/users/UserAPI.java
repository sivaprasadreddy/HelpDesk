package dev.sivalabs.helpdesk.users;

import java.util.List;
import org.springframework.stereotype.Component;

/** The users module's facade for other modules. */
@Component
public class UserAPI {
    private final UserService userService;

    UserAPI(UserService userService) {
        this.userService = userService;
    }

    /** Ids of active staff members who handle the category, in ascending order. */
    public List<Long> findActiveStaffIdsHandling(Long categoryId) {
        return userService.findActiveStaffIdsHandling(categoryId);
    }
}
