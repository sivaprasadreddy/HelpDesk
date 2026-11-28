package dev.sivalabs.helpdesk.users;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The users module's facade for other modules: accounts, without role-specific profiles. */
@Component
public class UserAPI {
    private final UserService userService;

    UserAPI(UserService userService) {
        this.userService = userService;
    }

    public Optional<UserDto> findById(Long id) {
        return userService.findById(id);
    }

    /** Every user with the role, active or not, ordered by name. */
    public List<UserDto> findUsersByRole(Role role) {
        return userService.findUsersByRole(role);
    }

    /**
     * Creates an account and returns its id; joins the caller's transaction.
     *
     * @throws EmailAlreadyExistsException when the email address is in use
     */
    public Long createUser(CreateUserCmd cmd) {
        return userService.createUser(cmd);
    }

    /** @throws EmailAlreadyExistsException when another user has the email address */
    public void updateNameAndEmail(Long id, String name, String email) {
        userService.updateNameAndEmail(id, name, email);
    }

    /** Activates or deactivates an account; deactivated users cannot sign in. */
    public void setActive(Long id, boolean active) {
        userService.setActive(id, active);
    }

    /** Names and email addresses by user id; ids that do not exist are left out. */
    public Map<Long, UserContact> findContactsByIds(Collection<Long> ids) {
        return userService.findContactsByIds(ids);
    }

    /** Display names by user id; ids that do not exist are left out. */
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        return userService.findNamesByIds(ids);
    }
}
