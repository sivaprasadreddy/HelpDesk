package dev.sivalabs.helpdesk.users;

import org.springframework.stereotype.Controller;

@Controller
class UserController {
    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }
}
