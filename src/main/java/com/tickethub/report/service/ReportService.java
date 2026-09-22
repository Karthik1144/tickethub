package com.tickethub.report.service;

import com.tickethub.catalogue.domain.Show;
import com.tickethub.catalogue.repository.ShowRepository;
import com.tickethub.common.exception.ApiException;
import com.tickethub.report.dto.ReportDtos.*;
import com.tickethub.report.repository.ReportRepository;
import com.tickethub.seating.domain.SeatStatus;
import com.tickethub.seating.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ReportService(ReportRepository reportRepository,
                         ShowRepository showRepository,
                         ShowSeatRepository showSeatRepository) {
        this.reportRepository = reportRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional(readOnly = true)
    public SalesReport sales(Instant from, Instant to) {
        List<SalesRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] row : reportRepository.salesByEvent(from, to)) {
            Long eventId = ((Number) row[0]).longValue();
            String title = (String) row[1];
            long bookings = ((Number) row[2]).longValue();
            long seats = ((Number) row[3]).longValue();
            BigDecimal revenue = toBigDecimal(row[4]);
            rows.add(new SalesRow(eventId, title, bookings, seats, revenue));
            total = total.add(revenue);
        }
        return new SalesReport(rows, total);
    }

    @Transactional(readOnly = true)
    public OccupancyReport occupancy(Long showId) {
        Show show = showRepository.findDetailById(showId).orElseThrow(() -> ApiException.notFound("Show"));

        long booked = showSeatRepository.countByShowIdAndStatus(showId, SeatStatus.BOOKED);
        long held = showSeatRepository.countByShowIdAndStatus(showId, SeatStatus.HELD);
        long available = showSeatRepository.countByShowIdAndStatus(showId, SeatStatus.AVAILABLE);
        long totalSeats = booked + held + available;

        double percent = totalSeats == 0 ? 0.0
                : BigDecimal.valueOf(booked * 100.0 / totalSeats).setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new OccupancyReport(showId, show.getEvent().getTitle(), totalSeats, booked, held, percent);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }
}
