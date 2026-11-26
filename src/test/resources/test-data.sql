DELETE FROM comments;
DELETE FROM tickets;
DELETE FROM staff_categories;
DELETE FROM categories;
DELETE FROM users;

insert into users(id, email, password, name, role, created_at) values
(1, 'admin@gmail.com', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'Administrator', 'ROLE_ADMIN', CURRENT_TIMESTAMP)
;
