package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.shared.BaseEntity;
import jakarta.persistence.*;

/** An image attached to a ticket, stored in the database. */
@Entity
@Table(name = "ticket_attachments")
class TicketAttachmentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_attachment_id_generator")
    @SequenceGenerator(
            name = "ticket_attachment_id_generator",
            sequenceName = "ticket_attachment_id_seq",
            allocationSize = 50)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] data;

    protected TicketAttachmentEntity() {}

    TicketAttachmentEntity(Long ticketId, String fileName, String contentType, byte[] data) {
        this.ticketId = ticketId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = data.length;
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getData() {
        return data;
    }
}
