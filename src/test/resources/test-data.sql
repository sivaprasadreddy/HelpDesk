DELETE FROM comments;
DELETE FROM tickets;
DELETE FROM staff_categories;
DELETE FROM categories;
DELETE FROM users;

insert into users(id, email, password, name, role, created_at) values
(1, 'admin@gmail.com', '$2a$10$hKDVYxLefVHV/vtuPhWD3OigtRyOykRLDdUAp80Z1crSoS1lFqaFS', 'Administrator', 'ROLE_ADMIN', CURRENT_TIMESTAMP)
;
insert into categories(id, name, description) values
(1, 'Plumbing', 'Leaks, blocked drains, taps, pipes and water supply'),
(2, 'Electrical', 'Power outages, wiring, switches, sockets and lighting'),
(3, 'Carpentry', 'Doors, windows, furniture and woodwork repairs'),
(4, 'Housekeeping', 'Cleaning of common areas and waste collection'),
(5, 'Security', 'Gates, access control, CCTV and safety concerns'),
(6, 'Lift Maintenance', 'Lift breakdowns, servicing and safety checks'),
(7, 'Gardening', 'Lawns, plants, trees and landscaping')
;
