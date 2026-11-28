package dev.sivalabs.helpdesk.staff;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import dev.sivalabs.helpdesk.users.CreateUserCmd;
import dev.sivalabs.helpdesk.users.ProfileFields;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.UserAPI;
import dev.sivalabs.helpdesk.users.UserDto;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StaffService {
    private static final Logger LOG = LoggerFactory.getLogger(StaffService.class);

    private final UserAPI userAPI;
    private final CategoryAPI categoryAPI;
    private final StaffProfileRepository staffProfileRepository;
    private final StaffCategoryRepository staffCategoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    StaffService(
            UserAPI userAPI,
            CategoryAPI categoryAPI,
            StaffProfileRepository staffProfileRepository,
            StaffCategoryRepository staffCategoryRepository,
            ApplicationEventPublisher eventPublisher) {
        this.userAPI = userAPI;
        this.categoryAPI = categoryAPI;
        this.staffProfileRepository = staffProfileRepository;
        this.staffCategoryRepository = staffCategoryRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Every staff member, active or not, ordered by name. */
    @Transactional(readOnly = true)
    public List<StaffDto> findStaffMembers() {
        var users = userAPI.findUsersByRole(Role.ROLE_STAFF);
        var ids = users.stream().map(UserDto::id).toList();
        var profiles = staffProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StaffProfileEntity::getUserId, Function.identity()));
        var categories = staffCategoryRepository.findCategoryIds(ids);
        return users.stream().map(u -> toStaffDto(u, profiles, categories)).toList();
    }

    @Transactional(readOnly = true)
    public StaffDto getStaffMember(Long id) {
        var user = loadStaff(id);
        var profiles =
                staffProfileRepository.findById(id).map(p -> Map.of(id, p)).orElse(Map.of());
        return toStaffDto(user, profiles, staffCategoryRepository.findCategoryIds(List.of(id)));
    }

    /** Ids of active staff members who handle the category, in ascending order. */
    @Transactional(readOnly = true)
    public List<Long> findActiveStaffIdsHandling(Long categoryId) {
        var candidates = staffCategoryRepository.findStaffIdsByCategoryId(categoryId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        var active = userAPI.findUsersByRole(Role.ROLE_STAFF).stream()
                .filter(UserDto::active)
                .map(UserDto::id)
                .collect(Collectors.toSet());
        return candidates.stream().filter(active::contains).toList();
    }

    @Transactional
    public void createStaffMember(CreateStaffCmd cmd) {
        var id = userAPI.createUser(new CreateUserCmd(cmd.name(), cmd.email(), cmd.password(), Role.ROLE_STAFF));
        staffProfileRepository.save(new StaffProfileEntity(
                id, ProfileFields.clean(cmd.mobile()), ProfileFields.clean(cmd.permanentAddress())));
    }

    /**
     * Updates a staff member's details and the categories they handle. Newly added categories must be active;
     * already assigned categories that were deactivated since may be kept.
     */
    @Transactional
    public void updateStaffMember(UpdateStaffCmd cmd) {
        loadStaff(cmd.id());
        var assigned =
                staffCategoryRepository.findCategoryIds(List.of(cmd.id())).getOrDefault(cmd.id(), Set.of());
        var allowed = new HashSet<>(assigned);
        categoryAPI.findActiveCategories().forEach(c -> allowed.add(c.id()));
        if (!allowed.containsAll(cmd.categoryIds())) {
            throw new InvalidCategoryException();
        }
        userAPI.updateNameAndEmail(cmd.id(), cmd.name(), cmd.email());
        staffCategoryRepository.replaceCategoryIds(cmd.id(), cmd.categoryIds());
        var mobile = ProfileFields.clean(cmd.mobile());
        var address = ProfileFields.clean(cmd.permanentAddress());
        staffProfileRepository
                .findById(cmd.id())
                .ifPresentOrElse(
                        p -> {
                            p.setMobile(mobile);
                            p.setPermanentAddress(address);
                        },
                        () -> staffProfileRepository.save(new StaffProfileEntity(cmd.id(), mobile, address)));
        LOG.info("Staff member {} updated; handles categories {}", cmd.id(), cmd.categoryIds());
    }

    /** Deactivating an active staff member publishes {@link StaffDeactivatedEvent}. */
    @Transactional
    public void setStaffActive(Long id, boolean active) {
        var staff = loadStaff(id);
        var deactivating = staff.active() && !active;
        userAPI.setActive(id, active);
        LOG.info("Staff member {} {}", id, active ? "activated" : "deactivated");
        if (deactivating) {
            eventPublisher.publishEvent(new StaffDeactivatedEvent(id));
        }
    }

    private UserDto loadStaff(Long id) {
        return userAPI.findById(id)
                .filter(u -> u.role() == Role.ROLE_STAFF)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member " + id + " not found"));
    }

    private static StaffDto toStaffDto(
            UserDto user, Map<Long, StaffProfileEntity> profiles, Map<Long, Set<Long>> categories) {
        var profile = profiles.get(user.id());
        return new StaffDto(
                user.id(),
                user.name(),
                user.email(),
                user.active(),
                Set.copyOf(categories.getOrDefault(user.id(), Set.of())),
                user.createdAt(),
                profile == null ? null : profile.getMobile(),
                profile == null ? null : profile.getPermanentAddress());
    }
}
