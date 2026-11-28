package dev.sivalabs.helpdesk.staff;

import java.util.List;
import org.springframework.stereotype.Component;

/** The staff module's facade for other modules. */
@Component
public class StaffAPI {
    private final StaffService staffService;

    StaffAPI(StaffService staffService) {
        this.staffService = staffService;
    }

    /** Ids of active staff members who handle the category, in ascending order. */
    public List<Long> findActiveStaffIdsHandling(Long categoryId) {
        return staffService.findActiveStaffIdsHandling(categoryId);
    }

    /** Every staff member, active or not, ordered by name. */
    public List<StaffDto> findStaffMembers() {
        return staffService.findStaffMembers();
    }

    /** Whether the id is a staff member who is active (and so can be given tickets). */
    public boolean isActiveStaff(Long id) {
        return findStaffMembers().stream().anyMatch(s -> s.id().equals(id) && s.active());
    }
}
