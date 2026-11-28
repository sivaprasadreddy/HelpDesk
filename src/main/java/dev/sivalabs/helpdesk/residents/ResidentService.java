package dev.sivalabs.helpdesk.residents;

import dev.sivalabs.helpdesk.users.CreateUserCmd;
import dev.sivalabs.helpdesk.users.ProfileFields;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.UserAPI;
import dev.sivalabs.helpdesk.users.UserDto;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ResidentService {
    private final UserAPI userAPI;
    private final ResidentProfileRepository residentProfileRepository;

    ResidentService(UserAPI userAPI, ResidentProfileRepository residentProfileRepository) {
        this.userAPI = userAPI;
        this.residentProfileRepository = residentProfileRepository;
    }

    /** Every resident, active or not, ordered by name. */
    @Transactional(readOnly = true)
    public List<ResidentDto> findResidents() {
        var users = userAPI.findUsersByRole(Role.ROLE_USER);
        var profiles = residentProfileRepository
                .findAllById(users.stream().map(UserDto::id).toList())
                .stream()
                .collect(Collectors.toMap(ResidentProfileEntity::getUserId, Function.identity()));
        return users.stream()
                .map(u -> {
                    var p = profiles.get(u.id());
                    return new ResidentDto(
                            u.id(),
                            u.name(),
                            u.email(),
                            u.active(),
                            u.createdAt(),
                            p == null ? null : p.getFlatNumber(),
                            p == null ? null : p.getMobile(),
                            p == null ? null : p.getPermanentAddress());
                })
                .toList();
    }

    @Transactional
    public void createResident(CreateResidentCmd cmd) {
        var id = userAPI.createUser(new CreateUserCmd(cmd.name(), cmd.email(), cmd.password(), Role.ROLE_USER));
        residentProfileRepository.save(new ResidentProfileEntity(
                id,
                ProfileFields.clean(cmd.flatNumber()),
                ProfileFields.clean(cmd.mobile()),
                ProfileFields.clean(cmd.permanentAddress())));
    }
}
