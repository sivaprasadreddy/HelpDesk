package dev.sivalabs.helpdesk.live;

import dev.sivalabs.helpdesk.users.SecurityUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** The event stream a signed-in page listens to ({@code static/js/live.js}). */
@RestController
class LiveUpdateController {
    private final LiveUpdateHub hub;

    LiveUpdateController(LiveUpdateHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/live/tickets", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter ticketUpdates(@AuthenticationPrincipal SecurityUser user) {
        return hub.subscribe(user);
    }
}
