package dev.sivalabs.helpdesk.users;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserService {
    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> findById(Long id) {
        return userRepository.findById(id).map(UserService::toUserDto);
    }

    @Transactional(readOnly = true)
    public List<UserDto> findUsersByRole(Role role) {
        return userRepository.findByRoleOrderByNameAsc(role).stream()
                .map(UserService::toUserDto)
                .toList();
    }

    @Transactional
    public Long createUser(CreateUserCmd cmd) {
        var email = normalise(cmd.email());
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
        LOG.info("Created {} account {}", cmd.role(), user.getId());
        return user.getId();
    }

    @Transactional
    public void updateNameAndEmail(Long id, String name, String email) {
        var user = load(id);
        var normalised = normalise(email);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalised, id)) {
            throw new EmailAlreadyExistsException(normalised);
        }
        user.setName(name.trim());
        user.setEmail(normalised);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        load(id).setActive(active);
        userRepository.flush();
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

    private UserEntity load(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User " + id + " not found"));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserDto toUserDto(UserEntity user) {
        return new UserDto(
                user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
