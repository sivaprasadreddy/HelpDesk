package dev.sivalabs.helpdesk.residents;

import org.springframework.data.jpa.repository.JpaRepository;

interface ResidentProfileRepository extends JpaRepository<ResidentProfileEntity, Long> {}
