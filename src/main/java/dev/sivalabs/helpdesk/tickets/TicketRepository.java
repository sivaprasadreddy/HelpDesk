package dev.sivalabs.helpdesk.tickets;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    /** Number of tickets in the given statuses per assignee; assignees with none are absent. */
    @Query("""
            select t.assignedTo as staffId, count(t) as tickets from TicketEntity t
            where t.assignedTo in :staffIds and t.status in :statuses
            group by t.assignedTo
            """)
    List<StaffWorkload> countByAssignee(Collection<Long> staffIds, Collection<TicketStatus> statuses);

    Page<TicketEntity> findByCreatedBy(Long createdBy, Pageable pageable);

    Page<TicketEntity> findByCreatedByAndStatus(Long createdBy, TicketStatus status, Pageable pageable);

    /** Number of tickets per status raised by the user; statuses with none are absent. */
    @Query("""
            select t.status as status, count(t) as tickets from TicketEntity t
            where t.createdBy = :createdBy
            group by t.status
            """)
    List<StatusCount> countByStatusForCreator(Long createdBy);

    interface StaffWorkload {
        Long getStaffId();

        long getTickets();
    }

    interface StatusCount {
        TicketStatus getStatus();

        long getTickets();
    }
}
