# HelpDesk

HelpDesk is a web application for raising, tracking and resolving maintenance and service requests in a gated community.
Residents report issues (a leaking pipe, a power outage, a broken lift) as tickets, which are assigned to staff members who work on them until they are resolved.
Administrators oversee the whole process and use analytics to monitor support performance.

## Actors

| Role      | Description                                                                 |
|-----------|-----------------------------------------------------------------------------|
| **ADMIN** | Manages the help desk: oversees all tickets, manages staff and categories, assigns work. |
| **STAFF** | Maintenance and service staff who handle one or more ticket categories and resolve assigned tickets. |
| **USER**  | Residents who raise tickets and follow their progress.                      |

## Key Features

### Ticket Creation
- ADMIN and USER can create tickets.
- A ticket captures a title, description, priority and category.

### Ticket Categories
- Every ticket belongs to a category (e.g. Plumbing, Electrical, Carpentry, Housekeeping, Security, Lift Maintenance, Gardening).
- Each staff member handles one or more categories; a category can be handled by several staff members.

### Ticket Assignment
- New tickets are **automatically assigned** to a staff member who handles the ticket's category.
- An ADMIN can **manually assign or reassign** a ticket to any staff member.

### Ticket Handling (STAFF)
- View tickets assigned to them.
- Update the ticket status (e.g. `OPEN` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`).
- Add comments to tickets.

### User Self-Service (USER)
- View the tickets they created and track their status.
- Add comments to their own tickets.

### Administration (ADMIN)
- View all tickets in the system.
- Update any ticket (status, priority, assignee, details).
- Manage staff members (add, update, deactivate).
- Manage ticket categories (add, update, deactivate).
- Assign one or more categories to each staff member.

### Analytics
- Dashboard with ticket metrics, such as:
  - Tickets by status, priority and category
  - Tickets created vs. resolved over time
  - Average resolution time
  - Workload and performance per staff member

## Permissions Summary

| Capability                  | ADMIN | STAFF         | USER       |
|-----------------------------|:-----:|:-------------:|:----------:|
| Create tickets              | ✅    | ❌            | ✅         |
| View tickets                | All   | Assigned      | Own        |
| Assign / reassign tickets   | ✅    | ❌            | ❌         |
| Update ticket status        | ✅    | Assigned      | ❌         |
| Add comments                | ✅    | Assigned      | Own        |
| Manage staff                | ✅    | ❌            | ❌         |
| Manage ticket categories    | ✅    | ❌            | ❌         |
| View analytics              | ✅    | ❌            | ❌         |
