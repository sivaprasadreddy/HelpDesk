package dev.sivalabs.helpdesk.users;

import org.springframework.data.jpa.repository.JpaRepository;

interface ResidentProfileRepository extends JpaRepository<ResidentProfileEntity, Long> {}
