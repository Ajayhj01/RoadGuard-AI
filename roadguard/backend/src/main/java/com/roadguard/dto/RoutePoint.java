package com.roadguard.dto;

import jakarta.validation.constraints.NotNull;

public class RoutePoint {

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;

    public RoutePoint() {}

    public RoutePoint(Double lat, Double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
}
