package dev.sivalabs.helpdesk.tickets;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    List<CommentEntity> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
