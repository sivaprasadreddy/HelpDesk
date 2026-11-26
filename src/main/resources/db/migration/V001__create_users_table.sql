create sequence user_id_seq start with 100 increment by 50;

create table users
(
    id         bigint    not null default nextval('user_id_seq'),
    email      text      not null,
    password   text      not null,
    name       text      not null,
    role       text      not null,
    active     boolean   not null default true,
    created_at timestamp not null default CURRENT_TIMESTAMP,
    updated_at timestamp,
    version    bigint       not null default 0,
    primary key (id),
    constraint user_email_unique unique (email),
    constraint user_role_check check (role in ('ROLE_ADMIN', 'ROLE_STAFF', 'ROLE_USER'))
);

insert into users(id, email, password, name, role, created_at) values
(1, 'admin@gmail.com', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'Administrator', 'ROLE_ADMIN', CURRENT_TIMESTAMP)
;