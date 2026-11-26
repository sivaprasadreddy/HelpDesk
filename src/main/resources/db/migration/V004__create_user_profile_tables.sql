-- Role-specific details, one row per user, kept out of the shared users table.
create table resident_profiles
(
    user_id           bigint not null,
    flat_number       text   not null,
    mobile            text   not null,
    permanent_address text,
    version           bigint not null default 0,
    primary key (user_id),
    constraint resident_profiles_user_fk foreign key (user_id) references users (id) on delete cascade
);

create table staff_profiles
(
    user_id           bigint not null,
    mobile            text   not null,
    permanent_address text,
    version           bigint not null default 0,
    primary key (user_id),
    constraint staff_profiles_user_fk foreign key (user_id) references users (id) on delete cascade
);
