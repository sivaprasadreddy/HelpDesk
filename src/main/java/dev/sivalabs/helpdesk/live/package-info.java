/**
 * Live updates: pushes ticket changes to the pages people have open, over server-sent events, so ticket details and
 * ticket lists refresh without a reload. Fed by the tickets module's RabbitMQ events.
 */
@ApplicationModule(displayName = "Live updates")
package dev.sivalabs.helpdesk.live;

import org.springframework.modulith.ApplicationModule;
