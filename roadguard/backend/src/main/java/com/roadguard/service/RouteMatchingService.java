package com.roadguard.service;

import com.roadguard.dto.RouteHazardRequest;
import com.roadguard.dto.RouteHazardResult;
import com.roadguard.dto.RoutePoint;
import com.roadguard.entity.HazardStatus;
import com.roadguard.entity.RoadHazard;
import com.roadguard.repository.RoadHazardRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RouteMatchingService {

    private static final double EARTH_RADIUS_M = 6371000.0;

    private final RoadHazardRepository hazardRepository;

    public RouteMatchingService(RoadHazardRepository hazardRepository) {
        this.hazardRepository = hazardRepository;
    }

    /** Great-circle distance between two lat/lng points, in meters. */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * For every ACTIVE hazard, find its closest point on the given route
     * (approximated as closest among the supplied route points — good
     * enough when the frontend sends a reasonably dense polyline from a
     * routing engine). Returns hazards within thresholdMeters, each
     * tagged with how far along the route it sits (for ordering + the
     * "X meters ahead" voice alerts) and how far off the route line it is.
     */
    public List<RouteHazardResult> findHazardsOnRoute(RouteHazardRequest request) {
        List<RoutePoint> points = request.getPoints();
        double threshold = request.getThresholdMeters() == null ? 60.0 : request.getThresholdMeters();

        // cumulative distance along the route up to each point index
        double[] cumulative = new double[points.size()];
        cumulative[0] = 0;
        for (int i = 1; i < points.size(); i++) {
            RoutePoint a = points.get(i - 1);
            RoutePoint b = points.get(i);
            cumulative[i] = cumulative[i - 1] + haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        }

        List<RoadHazard> activeHazards = hazardRepository.findByStatus(HazardStatus.ACTIVE);
        List<RouteHazardResult> results = new ArrayList<>();

        for (RoadHazard hazard : activeHazards) {
            double bestDistance = Double.MAX_VALUE;
            double bestCumulative = 0;

            for (int i = 0; i < points.size(); i++) {
                RoutePoint p = points.get(i);
                double d = haversineMeters(hazard.getLatitude(), hazard.getLongitude(), p.getLat(), p.getLng());
                if (d < bestDistance) {
                    bestDistance = d;
                    bestCumulative = cumulative[i];
                }
            }

            if (bestDistance <= threshold) {
                results.add(new RouteHazardResult(hazard, bestCumulative, bestDistance));
            }
        }

        results.sort(Comparator.comparingDouble(RouteHazardResult::getDistanceAlongRouteMeters));
        return results;
    }
}
