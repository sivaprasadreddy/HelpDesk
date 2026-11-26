create sequence category_id_seq start with 100 increment by 50;

create table categories
(
    id          bigint    not null default nextval('category_id_seq'),
    name        text      not null,
    description text,
    active      boolean   not null default true,
    created_at  timestamptz not null default CURRENT_TIMESTAMP,
    updated_at  timestamptz,
    version     bigint    not null default 0,
    primary key (id),
    constraint category_name_unique unique (name)
);

-- Categories handled by each staff member (many-to-many)
create table staff_categories
(
    user_id     bigint not null references users (id) on delete cascade,
    category_id bigint not null references categories (id) on delete cascade,
    primary key (user_id, category_id)
);

create index staff_categories_category_id_idx on staff_categories (category_id);

create sequence ticket_id_seq start with 100 increment by 50;

create table tickets
(
    id          bigint    not null default nextval('ticket_id_seq'),
    title       text      not null,
    description text      not null,
    priority    text      not null,
    status      text      not null default 'OPEN',
    category_id bigint    not null references categories (id),
    created_by  bigint    not null references users (id),
    assigned_to bigint references users (id),
    resolved_at timestamptz,
    closed_at   timestamptz,
    created_at  timestamptz not null default CURRENT_TIMESTAMP,
    updated_at  timestamptz,
    version     bigint    not null default 0,
    primary key (id),
    constraint ticket_priority_check check (priority in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    constraint ticket_status_check check (status in ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

create index tickets_status_idx on tickets (status);
create index tickets_category_id_idx on tickets (category_id);
create index tickets_created_by_idx on tickets (created_by);
create index tickets_assigned_to_idx on tickets (assigned_to);
create index tickets_created_at_idx on tickets (created_at);

create sequence comment_id_seq start with 100 increment by 50;

create table comments
(
    id         bigint    not null default nextval('comment_id_seq'),
    ticket_id  bigint    not null references tickets (id) on delete cascade,
    author_id  bigint    not null references users (id),
    content    text      not null,
    created_at timestamptz not null default CURRENT_TIMESTAMP,
    updated_at timestamptz,
    version    bigint    not null default 0,
    primary key (id)
);

create index comments_ticket_id_idx on comments (ticket_id);
