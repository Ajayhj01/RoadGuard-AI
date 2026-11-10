package com.roadguard.dto;

import com.roadguard.entity.RoadHazard;

public class RouteHazardResult {
    private RoadHazard hazard;
    private double distanceAlongRouteMeters;
    private double distanceFromRouteMeters;

    public RouteHazardResult(RoadHazard hazard, double distanceAlongRouteMeters, double distanceFromRouteMeters) {
        this.hazard = hazard;
        this.distanceAlongRouteMeters = distanceAlongRouteMeters;
        this.distanceFromRouteMeters = distanceFromRouteMeters;
    }

    public RoadHazard getHazard() { return hazard; }
    public double getDistanceAlongRouteMeters() { return distanceAlongRouteMeters; }
    public double getDistanceFromRouteMeters() { return distanceFromRouteMeters; }
}
