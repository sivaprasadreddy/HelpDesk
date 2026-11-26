package dev.sivalabs.helpdesk.analytics;

import dev.sivalabs.helpdesk.tickets.CountByGroup;
import dev.sivalabs.helpdesk.tickets.TicketAnalyticsAPI;
import dev.sivalabs.helpdesk.tickets.TicketStatus;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The analytics dashboard (ADMIN only), over the last 7, 30 or 90 days. */
@Controller
class AnalyticsController {
    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsController.class);

    static final List<Integer> PERIODS = List.of(7, 30, 90);
    static final int DEFAULT_PERIOD = 30;

    private final TicketAnalyticsAPI analytics;
    private final AccessPolicy accessPolicy;

    AnalyticsController(TicketAnalyticsAPI analytics, AccessPolicy accessPolicy) {
        this.analytics = analytics;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/admin/analytics")
    String dashboard(
            Authentication authentication, @RequestParam(required = false) @Nullable Integer days, Model model) {
        if (!accessPolicy.canViewAnalytics(authentication.getPrincipal())) {
            throw new AccessDeniedException("Only administrators can view analytics");
        }
        int period = days != null && PERIODS.contains(days) ? days : DEFAULT_PERIOD;
        var to = LocalDate.now(ZoneId.systemDefault());
        var from = to.minusDays(period - 1L);

        LOG.debug("Analytics dashboard requested for {} to {}", from, to);
        var breakdowns = analytics.getBreakdowns();
        var flow = analytics.getCreatedVsResolved(from, to);
        var resolution = analytics.getResolutionTimes(from, to);

        model.addAttribute("periods", PERIODS);
        model.addAttribute("period", period);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("breakdowns", breakdowns);
        model.addAttribute("activeCount", count(breakdowns.byStatus(), TicketStatus.OPEN, TicketStatus.IN_PROGRESS));
        model.addAttribute(
                "createdInPeriod", flow.stream().mapToLong(f -> f.created()).sum());
        model.addAttribute("flow", flow);
        model.addAttribute("resolvedInPeriod", resolution.resolvedCount());
        model.addAttribute("averageResolution", humanize(resolution.average()));
        model.addAttribute(
                "resolutionByPriority",
                resolution.byPriority().stream()
                        .map(p -> new PriorityRow(p.priority().getLabel(), p.resolvedCount(), humanize(p.average())))
                        .toList());
        model.addAttribute(
                "staff",
                analytics.getStaffPerformance(from, to).stream()
                        .map(p -> new StaffRow(
                                p.name(),
                                p.active(),
                                p.open(),
                                p.inProgress(),
                                p.workload(),
                                p.resolvedInPeriod(),
                                humanize(p.averageResolutionTime())))
                        .toList());
        return "analytics/dashboard";
    }

    record PriorityRow(String priority, long resolved, String average) {}

    record StaffRow(
            String name, boolean active, long open, long inProgress, long workload, long resolved, String average) {}

    private static long count(List<CountByGroup> groups, TicketStatus... statuses) {
        var keys = Arrays.stream(statuses).map(Enum::name).toList();
        return groups.stream()
                .filter(g -> keys.contains(g.key()))
                .mapToLong(CountByGroup::count)
                .sum();
    }

    /** A duration as "2d 4h", "3h 15m" or "45m"; "—" when there is none. Used by the template. */
    static String humanize(@Nullable Duration d) {
        if (d == null) {
            return "—";
        }
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(minutes, d.isZero() ? 0 : 1) + "m";
    }
}
