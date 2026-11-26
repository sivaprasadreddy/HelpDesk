package dev.sivalabs.helpdesk.users;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /** Every staff member, active or not, ordered by name. */
    public List<UserDto> findStaffMembers() {
        return userService.findUsersByRole(Role.ROLE_STAFF);
    }

    /** Display names by user id; ids that do not exist are left out. */
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        return userService.findNamesByIds(ids);
    }
}
