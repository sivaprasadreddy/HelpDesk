# Architecture Metrics

The metrics of `dev.sivalabs.helpdesk`, measured by Taikai. Every package counts on its own, without its subpackages. Packages and coupling count top-level classes, each together with the classes nested in it, while inheritance and cohesion count every class on its own. Only dependencies between the measured classes count, except for the libraries the packages use, and never those on the JDK. Packages and classes are named relative to `dev.sivalabs.helpdesk`. The lists show at most 10 classes each.

**Contents:** [Summary](#summary) · [Main Sequence](#main-sequence) · [Package Map](#package-map) · [Packages](#packages) · [Package Dependencies](#package-dependencies) · [Libraries](#libraries) · [Inheritance](#inheritance) · [Coupling](#coupling) · [Cohesion](#cohesion)

## Summary

| Metric | Value |
| --- | ---: |
| Packages | 14 |
| Classes | 125 |
| Abstract classes and interfaces | 9 |
| Average distance from the main sequence | 0.40 |
| Packages in the zone of pain | 6 |
| Packages in the zone of uselessness | 0 |
| Package cycles | 0 |
| Dependencies on less stable packages | 0 |
| Libraries | 9 |
| Maximum inheritance depth | 1 |

## Main Sequence

![Abstractness and instability of the packages](METRICS-main-sequence.svg)

Each dot is a package, sized by its number of classes. The abstractness A is the share of abstract classes and interfaces. The instability I = Ce / (Ca + Ce) is the share of outgoing couplings: Ca counts the classes in other packages that depend on the package, Ce the classes of the package that depend on other packages. A dot without room for its name has none: the packages are all listed below.

| Value | Meaning |
| --- | --- |
| A = 0 | Only concrete classes |
| A = 1 | Only abstract classes and interfaces |
| I = 0 | Stable: other packages depend on it and it depends on none, so a change affects many classes |
| I = 1 | Unstable: it depends on other packages and none depends on it, so it is easy to change |
| D = 0 | On the main sequence, the best |
| D = 1 | In a corner, as far from the main sequence as possible, the worst |

A stable package should be abstract, so that it can be extended without being changed, and an unstable package concrete. Packages on the main sequence A + I = 1 balance both, and the distance D = |A + I - 1| shows how far a package is from it. With a distance above 0.50, a concrete and stable package is in the zone of pain, hard to change as many classes depend on it, and an abstract and unstable package in the zone of uselessness, with abstractions that barely anything uses. Packages without couplings to other packages have no instability and are not shown.

## Package Map

![Map of the packages](METRICS-package-map.svg)

Each tile is a package: its area is its number of classes and its color its zone, the stronger the farther the package is from the main sequence.

## Packages

| Package | Classes | Abstract | Ca | Ce | I | A | D | Visibility | Libraries | Zone |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| [`dev.sivalabs.helpdesk`](../../../src/main/java/dev/sivalabs/helpdesk) | 2 | 0 | 0 | 0 | - | 0.00 | - | 1.00 | 1 |  |
| [`analytics`](../../../src/main/java/dev/sivalabs/helpdesk/analytics) | 1 | 0 | 0 | 1 | 1.00 | 0.00 | 0.00 | 0.00 | 2 |  |
| [`assistant`](../../../src/main/java/dev/sivalabs/helpdesk/assistant) | 10 | 0 | 0 | 4 | 1.00 | 0.00 | 0.00 | 0.30 | 4 |  |
| [`categories`](../../../src/main/java/dev/sivalabs/helpdesk/categories) | 8 | 1 | 8 | 2 | 0.20 | 0.13 | 0.68 | 0.50 | 4 | pain |
| [`config`](../../../src/main/java/dev/sivalabs/helpdesk/config) | 9 | 0 | 0 | 3 | 1.00 | 0.00 | 0.00 | 0.22 | 5 |  |
| [`home`](../../../src/main/java/dev/sivalabs/helpdesk/home) | 2 | 0 | 0 | 1 | 1.00 | 0.00 | 0.00 | 0.00 | 1 |  |
| [`knowledgebase`](../../../src/main/java/dev/sivalabs/helpdesk/knowledgebase) | 5 | 0 | 1 | 0 | 0.00 | 0.00 | 1.00 | 0.40 | 2 | pain |
| [`notifications`](../../../src/main/java/dev/sivalabs/helpdesk/notifications) | 3 | 0 | 0 | 1 | 1.00 | 0.00 | 0.00 | 0.00 | 3 |  |
| [`residents`](../../../src/main/java/dev/sivalabs/helpdesk/residents) | 7 | 1 | 0 | 2 | 1.00 | 0.14 | 0.14 | 0.00 | 3 |  |
| [`shared`](../../../src/main/java/dev/sivalabs/helpdesk/shared) | 3 | 1 | 10 | 0 | 0.00 | 0.33 | 0.67 | 1.00 | 2 | pain |
| [`shared.email`](../../../src/main/java/dev/sivalabs/helpdesk/shared/email) | 1 | 0 | 1 | 0 | 0.00 | 0.00 | 1.00 | 1.00 | 1 | pain |
| [`staff`](../../../src/main/java/dev/sivalabs/helpdesk/staff) | 13 | 1 | 3 | 2 | 0.40 | 0.08 | 0.52 | 0.23 | 4 | pain |
| [`tickets`](../../../src/main/java/dev/sivalabs/helpdesk/tickets) | 45 | 4 | 5 | 7 | 0.58 | 0.09 | 0.33 | 0.51 | 4 |  |
| [`users`](../../../src/main/java/dev/sivalabs/helpdesk/users) | 16 | 1 | 14 | 1 | 0.07 | 0.06 | 0.87 | 0.69 | 3 | pain |

Visibility is the relative visibility of Herbert Dowalil, the share of public classes, and Libraries the number of libraries that the package uses.

## Package Dependencies

![Dependencies between the packages](METRICS-dependencies.svg)

A package is drawn above the packages it depends on, so that the packages that many depend on are at the bottom, and is marked in the color of its zone. An arrow points from a package to a package it depends on, and the thicker it is, the more classes of the one depend on classes of the other. Red arrows point upwards and close a cycle: without them, the packages would have no cycles. Orange arrows point to a less stable package.

### Dependency Matrix

![Dependency structure matrix of the packages](METRICS-dependency-matrix.svg)

A row counts the pairs of classes with which its package depends on the package of each column, numbered as the rows. The packages are ordered from the bottom of the chart up, so that a package depends on the packages above it: the red cells above the diagonal close a cycle, as the red arrows do, a red frame surrounds the packages of a cycle, and the orange cells use a less stable package, as the orange arrows do.

### Package Cycles

None.

### Dependencies on Less Stable Packages

A package should only depend on packages that are at least as stable as itself, as the stable dependencies principle of Robert C. Martin demands: a package that many classes depend on is hard to change, so it should not depend on a package that changes more easily. These dependencies point to a package with a higher instability I, the largest increase first. They are orange in the chart and the matrix, unless they close a cycle.

None.

## Libraries

![Libraries used by the packages](METRICS-libraries.svg)

A row is a package, numbered as in the dependency matrix, and a column a library, named after the first two segments of the packages of its classes, such as `org.springframework` or `jakarta.persistence`. A cell counts the classes of the package that use the library. The JDK does not count, nor do the standard libraries of Kotlin, Groovy and Scala for the classes written in them, nor the classes of the project: those in the measured packages and in the package of at least two segments that they share.

| Library | Packages | Classes |
| --- | ---: | ---: |
| `org.springframework` | 14 | 58 |
| `org.slf4j` | 9 | 14 |
| `jakarta.validation` | 5 | 13 |
| `jakarta.persistence` | 6 | 9 |
| `jakarta.servlet` | 1 | 2 |
| `io.github` | 1 | 1 |
| `io.opentelemetry` | 1 | 1 |
| `org.flywaydb` | 1 | 1 |
| `org.thymeleaf` | 1 | 1 |

### Packages Without Libraries

None.

## Inheritance

The inheritance depth of a class counts its superclasses, its subclasses only the direct ones. Interfaces and anonymous classes do not count.

### Deepest Classes

| Class | Depth |
| --- | ---: |
| [`categories.CategoryEntity`](../../../src/main/java/dev/sivalabs/helpdesk/categories/CategoryEntity.java) | 1 |
| [`tickets.CommentEntity`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/CommentEntity.java) | 1 |
| [`tickets.TicketAttachmentEntity`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketAttachmentEntity.java) | 1 |
| [`tickets.TicketEntity`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketEntity.java) | 1 |
| [`users.UserEntity`](../../../src/main/java/dev/sivalabs/helpdesk/users/UserEntity.java) | 1 |

### Most Extended Classes

| Class | Subclasses |
| --- | ---: |
| [`shared.BaseEntity`](../../../src/main/java/dev/sivalabs/helpdesk/shared/BaseEntity.java) | 5 |

## Coupling

The fan-in of a class counts the classes that depend on it, its fan-out the classes it depends on.

### Most Used Classes

| Class | Fan-in |
| --- | ---: |
| [`tickets.TicketPriority`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketPriority.java) | 18 |
| [`tickets.TicketStatus`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketStatus.java) | 13 |
| [`users.Role`](../../../src/main/java/dev/sivalabs/helpdesk/users/Role.java) | 11 |
| [`categories.CategoryDto`](../../../src/main/java/dev/sivalabs/helpdesk/categories/CategoryDto.java) | 10 |
| [`tickets.TicketSnapshot`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketSnapshot.java) | 9 |
| [`categories.CategoryAPI`](../../../src/main/java/dev/sivalabs/helpdesk/categories/CategoryAPI.java) | 8 |
| [`tickets.TicketEvent`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketEvent.java) | 8 |
| [`users.SecurityUser`](../../../src/main/java/dev/sivalabs/helpdesk/users/SecurityUser.java) | 8 |
| [`assistant.TicketDraft`](../../../src/main/java/dev/sivalabs/helpdesk/assistant/TicketDraft.java) | 6 |
| [`staff.StaffDto`](../../../src/main/java/dev/sivalabs/helpdesk/staff/StaffDto.java) | 6 |

### Classes with the Most Dependencies

| Class | Fan-out |
| --- | ---: |
| [`tickets.TicketService`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketService.java) | 37 |
| [`tickets.TicketController`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketController.java) | 24 |
| [`staff.StaffService`](../../../src/main/java/dev/sivalabs/helpdesk/staff/StaffService.java) | 16 |
| [`tickets.TicketMcpTools`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketMcpTools.java) | 14 |
| [`notifications.TicketNotificationListener`](../../../src/main/java/dev/sivalabs/helpdesk/notifications/TicketNotificationListener.java) | 12 |
| [`tickets.TicketAnalyticsAPI`](../../../src/main/java/dev/sivalabs/helpdesk/tickets/TicketAnalyticsAPI.java) | 12 |
| [`assistant.AssistantService`](../../../src/main/java/dev/sivalabs/helpdesk/assistant/AssistantService.java) | 11 |
| [`staff.StaffController`](../../../src/main/java/dev/sivalabs/helpdesk/staff/StaffController.java) | 10 |
| [`analytics.AnalyticsController`](../../../src/main/java/dev/sivalabs/helpdesk/analytics/AnalyticsController.java) | 9 |
| [`residents.ResidentService`](../../../src/main/java/dev/sivalabs/helpdesk/residents/ResidentService.java) | 9 |

## Cohesion

The LCOM4 of a class counts the groups of its methods that have nothing in common: methods belong together if they use the same field or method of their object, also an inherited one, or call each other. Lower is better: 1 is best, as all methods work together. A higher value means that the class does as many unrelated things and can be split into as many classes. Constructors, static and abstract methods, getters and setters do not count, nor do methods without a connection to another member of their class. Interfaces, records and anonymous classes are not measured.

### Least Cohesive Classes

| Class | LCOM4 | Methods |
| --- | ---: | ---: |
| [`assistant.Conversation`](../../../src/main/java/dev/sivalabs/helpdesk/assistant/Conversation.java) | 2 | 8 |
