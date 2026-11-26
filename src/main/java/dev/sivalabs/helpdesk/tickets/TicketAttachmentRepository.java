package dev.sivalabs.helpdesk.tickets;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TicketAttachmentRepository extends JpaRepository<TicketAttachmentEntity, Long> {

    /** The ticket's attachments without their bytes, oldest first. */
    @Query("""
            select new dev.sivalabs.helpdesk.tickets.AttachmentView(a.id, a.fileName, a.contentType, a.sizeBytes)
            from TicketAttachmentEntity a where a.ticketId = :ticketId order by a.id
            """)
    List<AttachmentView> findViewsByTicketId(Long ticketId);

    Optional<TicketAttachmentEntity> findByIdAndTicketId(Long id, Long ticketId);
}
