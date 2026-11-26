-- Sample data for local development only (loaded by the 'local' profile).
-- Repeatable migration: runs after all versioned migrations; safe to re-run.
-- All non-admin accounts use the password "secret".

insert into users(id, email, password, name, role) values
(2, 'ravi.staff@gmail.com',   '$2a$10$/f2/DJU4m8Ir0r7LIGIEYOTAsCLlHledjBLG.0M02sAeJY2s7DdmW', 'Ravi Kumar',     'ROLE_STAFF'),
(3, 'anita.staff@gmail.com',  '$2a$10$/f2/DJU4m8Ir0r7LIGIEYOTAsCLlHledjBLG.0M02sAeJY2s7DdmW', 'Anita Sharma',   'ROLE_STAFF'),
(4, 'mohan.staff@gmail.com',  '$2a$10$/f2/DJU4m8Ir0r7LIGIEYOTAsCLlHledjBLG.0M02sAeJY2s7DdmW', 'Mohan Das',      'ROLE_STAFF'),
(5, 'lakshmi.staff@gmail.com','$2a$10$/f2/DJU4m8Ir0r7LIGIEYOTAsCLlHledjBLG.0M02sAeJY2s7DdmW', 'Lakshmi Iyer',   'ROLE_STAFF'),
(6, 'priya@gmail.com',        '$2a$10$/f2/DJU4m8Ir0r7LIGIEYOTAsCLlHledjBLG.0M02sAeJY2s7DdmW', 'Priya Nair',     'ROLE_USER'),
(7, 'arjun@gmail.com',        '$2a$10$/f2/DJU4m8Ir0r7LIGIEYOTAsCLlHledjBLG.0M02sAeJY2s7DdmW', 'Arjun Reddy',    'ROLE_USER'),
(8, 'meena@gmail.com',        '$2a$10$/f2/DJU4m8Ir0r7LIGIEYOTAsCLlHledjBLG.0M02sAeJY2s7DdmW', 'Meena Pillai',   'ROLE_USER')
on conflict do nothing;

insert into staff_categories(user_id, category_id) values
(2, 1), (2, 3),          -- Ravi: Plumbing, Carpentry
(3, 2), (3, 6),          -- Anita: Electrical, Lift Maintenance
(4, 4), (4, 7),          -- Mohan: Housekeeping, Gardening
(5, 5), (5, 2)           -- Lakshmi: Security, Electrical
on conflict do nothing;

insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to, resolved_at, closed_at, created_at) values
(1,  'Kitchen sink leaking',        'Water drips under the kitchen sink in flat A-101.',        'HIGH',     'OPEN',        1, 6, 2, null, null, CURRENT_TIMESTAMP - interval '1 day'),
(2,  'Power outage in B block',     'No power on the 3rd floor of B block since morning.',      'CRITICAL', 'IN_PROGRESS', 2, 7, 3, null, null, CURRENT_TIMESTAMP - interval '2 days'),
(3,  'Bedroom door hinge broken',   'The bedroom door in C-204 does not close properly.',       'LOW',      'RESOLVED',    3, 8, 2, CURRENT_TIMESTAMP - interval '3 days', null, CURRENT_TIMESTAMP - interval '6 days'),
(4,  'Lobby not cleaned',           'The A block lobby has not been cleaned for two days.',     'MEDIUM',   'CLOSED',      4, 6, 4, CURRENT_TIMESTAMP - interval '9 days', CURRENT_TIMESTAMP - interval '8 days', CURRENT_TIMESTAMP - interval '10 days'),
(5,  'Main gate boom barrier stuck','The boom barrier at the main gate stays open.',            'HIGH',     'OPEN',        5, 7, 5, null, null, CURRENT_TIMESTAMP - interval '5 hours'),
(6,  'Lift stuck between floors',   'The B block lift stopped between the 4th and 5th floor.',  'CRITICAL', 'RESOLVED',    6, 8, 3, CURRENT_TIMESTAMP - interval '13 days', null, CURRENT_TIMESTAMP - interval '14 days'),
(7,  'Overgrown hedges near park',  'Hedges along the children''s park need trimming.',         'LOW',      'IN_PROGRESS', 7, 6, 4, null, null, CURRENT_TIMESTAMP - interval '4 days'),
(8,  'Bathroom tap not working',    'The bathroom tap in D-302 gives no water.',                'MEDIUM',   'CLOSED',      1, 7, 2, CURRENT_TIMESTAMP - interval '19 days', CURRENT_TIMESTAMP - interval '18 days', CURRENT_TIMESTAMP - interval '21 days'),
(9,  'Corridor lights flickering',  'Corridor lights on the 2nd floor of C block flicker.',     'MEDIUM',   'OPEN',        2, 8, 5, null, null, CURRENT_TIMESTAMP - interval '3 days'),
(10, 'CCTV camera offline',         'The camera covering the parking area shows no feed.',      'HIGH',     'RESOLVED',    5, 6, 5, CURRENT_TIMESTAMP - interval '25 days', null, CURRENT_TIMESTAMP - interval '27 days')
on conflict do nothing;

insert into comments(id, ticket_id, author_id, content, created_at) values
(1, 2, 3, 'Checking the main distribution board for B block.',   CURRENT_TIMESTAMP - interval '1 day'),
(2, 2, 7, 'Thanks, please update when power is back.',           CURRENT_TIMESTAMP - interval '20 hours'),
(3, 3, 2, 'Replaced the hinge. Please confirm the door closes.', CURRENT_TIMESTAMP - interval '3 days'),
(4, 6, 3, 'Lift reset and serviced. Running normally now.',      CURRENT_TIMESTAMP - interval '13 days'),
(5, 7, 4, 'Trimming scheduled for this weekend.',                CURRENT_TIMESTAMP - interval '2 days')
on conflict do nothing;
