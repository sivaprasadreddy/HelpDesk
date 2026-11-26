package dev.sivalabs.helpdesk.tickets;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface TicketRepository extends JpaRepository<TicketEntity, Long>, JpaSpecificationExecutor<TicketEntity> {

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

    Page<TicketEntity> findByAssignedTo(Long assignedTo, Pageable pageable);

    Page<TicketEntity> findByAssignedToAndStatus(Long assignedTo, TicketStatus status, Pageable pageable);

    /** Number of tickets per status assigned to the user; statuses with none are absent. */
    @Query("""
            select t.status as status, count(t) as tickets from TicketEntity t
            where t.assignedTo = :assignedTo
            group by t.status
            """)
    List<StatusCount> countByStatusForAssignee(Long assignedTo);

    List<TicketEntity> findByAssignedToAndStatusInOrderByIdAsc(Long assignedTo, Collection<TicketStatus> statuses);

    @Query("select t.status as groupKey, count(t) as tickets from TicketEntity t group by t.status")
    List<GroupCount<TicketStatus>> countGroupedByStatus();

    @Query("select t.priority as groupKey, count(t) as tickets from TicketEntity t group by t.priority")
    List<GroupCount<TicketPriority>> countGroupedByPriority();

    @Query("select t.categoryId as groupKey, count(t) as tickets from TicketEntity t group by t.categoryId")
    List<GroupCount<Long>> countGroupedByCategory();

    interface GroupCount<K> {
        K getGroupKey();

        long getTickets();
    }

    interface StaffWorkload {
        Long getStaffId();

        long getTickets();
    }

    interface StatusCount {
        TicketStatus getStatus();

        long getTickets();
    }
}
