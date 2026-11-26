package dev.sivalabs.helpdesk.users;

import org.springframework.data.jpa.repository.JpaRepository;

interface StaffProfileRepository extends JpaRepository<StaffProfileEntity, Long> {}
