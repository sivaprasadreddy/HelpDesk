package dev.sivalabs.helpdesk.users;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    List<UserEntity> findByRoleOrderByNameAsc(Role role);

    @Query("""
            select u.id from UserEntity u join u.categoryIds c
            where u.role = dev.sivalabs.helpdesk.users.Role.ROLE_STAFF and u.active = true and c = :categoryId
            order by u.id
            """)
    List<Long> findActiveStaffIdsByCategoryId(Long categoryId);
}
