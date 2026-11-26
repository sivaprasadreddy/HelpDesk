package dev.sivalabs.helpdesk.tickets;

import org.springframework.data.jpa.repository.JpaRepository;

interface TicketRepository extends JpaRepository<TicketEntity, Long> {}
