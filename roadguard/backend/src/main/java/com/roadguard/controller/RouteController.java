package com.roadguard.controller;

import com.roadguard.dto.RouteHazardRequest;
import com.roadguard.dto.RouteHazardResult;
import com.roadguard.service.RouteMatchingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteMatchingService routeMatchingService;

    public RouteController(RouteMatchingService routeMatchingService) {
        this.routeMatchingService = routeMatchingService;
    }

    /**
     * Frontend flow: get route geometry from a routing engine (e.g. OSRM),
     * send the list of {lat,lng} points here, get back every known hazard
     * that sits on that route, ordered by distance along it.
     */
    @PostMapping("/hazards")
    public List<RouteHazardResult> hazardsOnRoute(@Valid @RequestBody RouteHazardRequest request) {
        return routeMatchingService.findHazardsOnRoute(request);
    }
}
