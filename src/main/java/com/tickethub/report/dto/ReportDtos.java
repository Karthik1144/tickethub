package com.tickethub.report.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {}

    public record SalesRow(Long eventId, String eventTitle, long bookings, long seats, BigDecimal revenue) {}

    public record SalesReport(List<SalesRow> rows, BigDecimal totalRevenue) {}

    public record OccupancyReport(Long showId, String eventTitle, long totalSeats,
                                  long bookedSeats, long heldSeats, double occupancyPercent) {}
}
