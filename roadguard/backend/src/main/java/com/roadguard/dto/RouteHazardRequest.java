package com.roadguard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The frontend gets the actual route geometry from a free routing service
 * (e.g. OSRM) and sends the list of points along it here. This endpoint
 * doesn't calculate routes itself — it only matches known hazards against
 * a route that was already calculated.
 */
public class RouteHazardRequest {

    @NotEmpty
    @Valid
    private List<RoutePoint> points;

    /** How close (in meters) a hazard must be to the route to count as "on it". Default 60m. */
    private Double thresholdMeters = 60.0;

    public List<RoutePoint> getPoints() { return points; }
    public void setPoints(List<RoutePoint> points) { this.points = points; }
    public Double getThresholdMeters() { return thresholdMeters; }
    public void setThresholdMeters(Double thresholdMeters) { this.thresholdMeters = thresholdMeters; }
}
