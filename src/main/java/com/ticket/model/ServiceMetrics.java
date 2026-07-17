package com.ticket.model;

public record ServiceMetrics(
    long ticketCount,
    double averageFirstResponseMinutes,
    double averageResolutionMinutes,
    double slaComplianceRate,
    double averageBacklogHours,
    double firstContactResolutionRate,
    double averageSatisfaction,
    long openBacklog,
    long breachedCount
) { }
