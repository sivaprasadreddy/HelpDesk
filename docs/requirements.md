# HelpDesk Requirements

Requirements derived from [project.md](project.md), listed in implementation-dependency order.
Each requirement depends only on requirements listed above it.

Status values: `TODO`, `IN_PROGRESS`, `DONE`.

| REQ_ID  | Title                                                                                              | Status |
|---------|----------------------------------------------------------------------------------------------------|--------|
| REQ-001 | Database schema and migrations for users, roles, categories, tickets and comments                  | DONE   |
| REQ-002 | Seed data: roles (ADMIN, STAFF, USER) and initial ADMIN account                                    | DONE   |
| REQ-003 | Seed data: default ticket categories (Plumbing, Electrical, Carpentry, Housekeeping, Security, Lift Maintenance, Gardening) | DONE   |
| REQ-004 | Seed data: sample staff, residents and tickets for local development (dev profile only)            | DONE   |
| REQ-005 | Authentication: login and logout                                                                   | DONE   |
| REQ-006 | ADMIN: list residents (USER) and staff (STAFF) on separate pages; only ADMIN can create a resident or staff member (no self-registration) | DONE   |
| REQ-007 | Role-based authorization and access rules (permissions matrix)                                    | DONE   |
| REQ-008 | Application shell: layout and role-based navigation                                                | DONE |
| REQ-009 | ADMIN: manage ticket categories (list, add, update, deactivate)                                    | DONE   |
| REQ-010 | ADMIN: manage staff members (update, deactivate)                                                    | DONE   |
| REQ-011 | ADMIN: assign one or more categories to a staff member                                             | DONE   |
| REQ-012 | Create ticket (ADMIN, USER) with title, description, priority and active category                 | DONE   |
| REQ-013 | Automatic assignment of new tickets to an active staff member handling the category               | TODO   |
| REQ-014 | USER: view own tickets and track their status                                                      | TODO   |
| REQ-015 | STAFF: view tickets assigned to them                                                               | TODO   |
| REQ-016 | ADMIN: view all tickets with filtering (status, priority, category, assignee)                      | TODO   |
| REQ-017 | Ticket details page                                                                                | TODO   |
| REQ-018 | Ticket status workflow (OPEN → IN_PROGRESS → RESOLVED → CLOSED) for STAFF (assigned) and ADMIN    | TODO   |
| REQ-019 | ADMIN: manually assign or reassign a ticket to any staff member                                    | TODO   |
| REQ-020 | ADMIN: update any ticket (status, priority, assignee, details)                                     | TODO   |
| REQ-021 | Ticket comments (ADMIN: any, STAFF: assigned, USER: own)                                           | TODO   |
| REQ-022 | Handling of tickets whose assignee or category is deactivated                                      | TODO   |
| REQ-023 | Analytics: tickets by status, priority and category                                                | TODO   |
| REQ-024 | Analytics: tickets created vs. resolved over time                                                  | TODO   |
| REQ-025 | Analytics: average resolution time                                                                 | TODO   |
| REQ-026 | Analytics: workload and performance per staff member                                               | TODO   |
| REQ-027 | Analytics dashboard page (ADMIN only)                                                              | TODO   |
