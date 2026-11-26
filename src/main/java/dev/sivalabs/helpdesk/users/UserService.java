package dev.sivalabs.helpdesk.users;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryAPI categoryService;

    UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, CategoryAPI categoryService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> findById(Long id) {
        return userRepository.findById(id).map(this::toUserDto);
    }

    @Transactional(readOnly = true)
    public List<UserDto> findUsersByRole(Role role) {
        return userRepository.findByRoleOrderByNameAsc(role).stream()
                .map(this::toUserDto)
                .toList();
    }

    @Transactional
    public void createUser(CreateUserCmd cmd) {
        var email = cmd.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        var user = new UserEntity();
        user.setName(cmd.name());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(cmd.password()));
        user.setRole(cmd.role());
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserDto getStaffMember(Long id) {
        return toUserDto(loadStaff(id));
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
    }

    @Transactional
    public void setStaffActive(Long id, boolean active) {
        loadStaff(id).setActive(active);
    }

    private UserEntity loadStaff(Long id) {
        return userRepository
                .findById(id)
                .filter(u -> u.getRole() == Role.ROLE_STAFF)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member " + id + " not found"));
    }

    private UserDto toUserDto(UserEntity user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                Set.copyOf(user.getCategoryIds()),
                user.getCreatedAt());
    }
}
