create sequence ticket_attachment_id_seq start with 100 increment by 50;

-- Images attached to a ticket when it is raised (at most 3 per ticket, enforced by the application).
create table ticket_attachments
(
    id           bigint    not null default nextval('ticket_attachment_id_seq'),
    ticket_id    bigint    not null references tickets (id) on delete cascade,
    file_name    text      not null,
    content_type text      not null,
    size_bytes   bigint    not null,
    data         bytea     not null,
    created_at   timestamp not null default CURRENT_TIMESTAMP,
    updated_at   timestamp,
    version      bigint    not null default 0,
    primary key (id)
);

create index ticket_attachments_ticket_id_idx on ticket_attachments (ticket_id);
