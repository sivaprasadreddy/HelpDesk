package dev.sivalabs.helpdesk.users;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserService {
    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final CategoryAPI categoryService;
    private final ResidentProfileRepository residentProfileRepository;
    private final StaffProfileRepository staffProfileRepository;

    UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CategoryAPI categoryService,
            ApplicationEventPublisher eventPublisher,
            ResidentProfileRepository residentProfileRepository,
            StaffProfileRepository staffProfileRepository) {
        this.userRepository = userRepository;
        this.residentProfileRepository = residentProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> findById(Long id) {
        return userRepository.findById(id).map(u -> toUserDto(u, Profiles.load(this, List.of(u))));
    }

    @Transactional(readOnly = true)
    public List<UserDto> findUsersByRole(Role role) {
        var users = userRepository.findByRoleOrderByNameAsc(role);
        var profiles = Profiles.load(this, users);
        return users.stream().map(u -> toUserDto(u, profiles)).toList();
    }

    @Transactional
    public void createUser(CreateUserCmd cmd) {
        var email = cmd.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            LOG.warn("Creating a {} account refused: the email address is already in use", cmd.role());
            throw new EmailAlreadyExistsException(email);
        }
        var user = new UserEntity();
        user.setName(cmd.name());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(cmd.password()));
        user.setRole(cmd.role());
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
        var mobile = ProfileFields.clean(cmd.mobile());
        var address = ProfileFields.clean(cmd.permanentAddress());
        switch (cmd.role()) {
            case ROLE_USER ->
                residentProfileRepository.save(new ResidentProfileEntity(
                        user.getId(), ProfileFields.clean(cmd.flatNumber()), mobile, address));
            case ROLE_STAFF -> staffProfileRepository.save(new StaffProfileEntity(user.getId(), mobile, address));
            case ROLE_ADMIN -> {}
        }
        LOG.info("Created {} account {}", cmd.role(), user.getId());
    }

    /** Ids of active staff members who handle the category, in ascending order. */
    @Transactional(readOnly = true)
    public List<Long> findActiveStaffIdsHandling(Long categoryId) {
        return userRepository.findActiveStaffIdsByCategoryId(categoryId);
    }

    @Transactional(readOnly = true)
    public Map<Long, UserContact> findContactsByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        UserEntity::getId, u -> new UserContact(u.getId(), u.getName(), u.getEmail())));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }

    @Transactional(readOnly = true)
    public UserDto getStaffMember(Long id) {
        var staff = loadStaff(id);
        return toUserDto(staff, Profiles.load(this, List.of(staff)));
    }

    /**
     * Updates a staff member's details and the categories they handle. Newly added categories must be active;
     * already assigned categories that were deactivated since may be kept.
     */
    @Transactional
    public void updateStaffMember(UpdateStaffCmd cmd) {
        var user = loadStaff(cmd.id());
        var email = cmd.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, cmd.id())) {
            throw new EmailAlreadyExistsException(email);
        }
        var allowed = new HashSet<>(user.getCategoryIds());
        categoryService.findActiveCategories().forEach(c -> allowed.add(c.id()));
        if (!allowed.containsAll(cmd.categoryIds())) {
            throw new InvalidCategoryException();
        }
        user.setName(cmd.name().trim());
        user.setEmail(email);
        user.setCategoryIds(cmd.categoryIds());
        var mobile = ProfileFields.clean(cmd.mobile());
        var address = ProfileFields.clean(cmd.permanentAddress());
        staffProfileRepository
                .findById(user.getId())
                .ifPresentOrElse(
                        p -> {
                            p.setMobile(mobile);
                            p.setPermanentAddress(address);
                        },
                        () -> staffProfileRepository.save(new StaffProfileEntity(user.getId(), mobile, address)));
        LOG.info("Staff member {} updated; handles categories {}", cmd.id(), cmd.categoryIds());
    }

    /** Deactivating an active staff member publishes {@link StaffDeactivatedEvent}. */
    @Transactional
    public void setStaffActive(Long id, boolean active) {
        var staff = loadStaff(id);
        var deactivating = staff.isActive() && !active;
        staff.setActive(active);
        LOG.info("Staff member {} {}", id, active ? "activated" : "deactivated");
        if (deactivating) {
            userRepository.flush();
            eventPublisher.publishEvent(new StaffDeactivatedEvent(id));
        }
    }

    private UserEntity loadStaff(Long id) {
        return userRepository
                .findById(id)
                .filter(u -> u.getRole() == Role.ROLE_STAFF)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member " + id + " not found"));
    }

    /** Residents' and staff profiles of a batch of users, by user id. */
    private record Profiles(Map<Long, ResidentProfileEntity> residents, Map<Long, StaffProfileEntity> staff) {
        static Profiles load(UserService service, Collection<UserEntity> users) {
            var residentIds = idsWithRole(users, Role.ROLE_USER);
            var staffIds = idsWithRole(users, Role.ROLE_STAFF);
            Map<Long, ResidentProfileEntity> residents = residentIds.isEmpty()
                    ? Map.of()
                    : service.residentProfileRepository.findAllById(residentIds).stream()
                            .collect(Collectors.toMap(ResidentProfileEntity::getUserId, p -> p));
            Map<Long, StaffProfileEntity> staff = staffIds.isEmpty()
                    ? Map.of()
                    : service.staffProfileRepository.findAllById(staffIds).stream()
                            .collect(Collectors.toMap(StaffProfileEntity::getUserId, p -> p));
            return new Profiles(residents, staff);
        }

        private static List<Long> idsWithRole(Collection<UserEntity> users, Role role) {
            return users.stream()
                    .filter(u -> u.getRole() == role)
                    .map(UserEntity::getId)
                    .toList();
        }
    }

    private UserDto toUserDto(UserEntity user, Profiles profiles) {
        String flatNumber = null;
        String mobile = null;
        String address = null;
        var resident = profiles.residents().get(user.getId());
        var staff = profiles.staff().get(user.getId());
        if (resident != null) {
            flatNumber = resident.getFlatNumber();
            mobile = resident.getMobile();
            address = resident.getPermanentAddress();
        } else if (staff != null) {
            mobile = staff.getMobile();
            address = staff.getPermanentAddress();
        }
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                Set.copyOf(user.getCategoryIds()),
                user.getCreatedAt(),
                flatNumber,
                mobile,
                address);
    }
}
