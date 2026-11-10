package com.roadguard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadguard.entity.HazardType;
import com.roadguard.entity.RoadHazard;
import com.roadguard.entity.Severity;
import com.roadguard.repository.RoadHazardRepository;
import com.roadguard.service.RouteMatchingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bridge between the citizen-facing upload flow and the Python detection
 * microservice. Supports two modes:
 *  - point mode (latitude/longitude): all detections saved at that one point
 *    (preferred — this is the real GPS location where the photo was taken)
 *  - route mode (routePoints): used only as a fallback when no GPS is
 *    available; detections spaced evenly along the route so they land
 *    within the RouteMatchingService threshold next time someone
 *    searches that same start -> end.
 */
@RestController
@RequestMapping("/api/detect")
public class DetectionController {

    private final RoadHazardRepository hazardRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${detection.service.url}")
    private String detectionServiceUrl;

    public DetectionController(RoadHazardRepository hazardRepository) {
        this.hazardRepository = hazardRepository;
    }

    /** Minimal shape for parsing the incoming route JSON — decoupled from the DTO used elsewhere. */
    private static class LatLng {
        public double lat;
        public double lng;
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public Map<String, Object> detect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "routePoints", required = false) String routePointsJson,
            @RequestParam(value = "startLabel", required = false) String startLabel,
            @RequestParam(value = "endLabel", required = false) String endLabel
    ) throws IOException {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });
        if (latitude != null) body.add("latitude", latitude);
        if (longitude != null) body.add("longitude", longitude);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        Map<String, Object> response = restTemplate.postForObject(
                detectionServiceUrl + "/detect", requestEntity, Map.class);

        List<RoadHazard> savedHazards = new ArrayList<>();

        if (response != null) {
            List<Map<String, Object>> detections = (List<Map<String, Object>>) response.get("detections");
            if (detections != null && !detections.isEmpty()) {

                List<LatLng> route = parseRoute(routePointsJson);

                // filter first, so spacing along the route uses the actual saved count
                List<Map<String, Object>> keep = new ArrayList<>();
                for (Map<String, Object> d : detections) {
                    double confidence = ((Number) d.get("confidence")).doubleValue();
                    if (confidence >= 0.5) keep.add(d);
                }

                double[] cumulative = null;
                double totalLength = 0;
                if (route != null && route.size() >= 2) {
                    cumulative = new double[route.size()];
                    cumulative[0] = 0;
                    for (int i = 1; i < route.size(); i++) {
                        LatLng a = route.get(i - 1), b = route.get(i);
                        cumulative[i] = cumulative[i - 1]
                                + RouteMatchingService.haversineMeters(a.lat, a.lng, b.lat, b.lng);
                    }
                    totalLength = cumulative[cumulative.length - 1];
                }

                for (int i = 0; i < keep.size(); i++) {
                    Map<String, Object> d = keep.get(i);
                    double confidence = ((Number) d.get("confidence")).doubleValue();
                    Severity severity = Severity.valueOf((String) d.get("severity"));

                    double hazardLat, hazardLng;
                    if (latitude != null && longitude != null) {
                        // real GPS from the device — always preferred when available
                        hazardLat = latitude;
                        hazardLng = longitude;
                    } else if (cumulative != null) {
                        // no GPS — fall back to spacing evenly between 10% and 90%
                        // of the route so pins don't sit exactly on the start/end markers
                        double frac = keep.size() == 1
                                ? 0.5
                                : 0.1 + (0.8 * i / (keep.size() - 1));
                        double targetDist = frac * totalLength;
                        double[] pt = pointAtDistance(route, cumulative, targetDist);
                        hazardLat = pt[0];
                        hazardLng = pt[1];
                    } else {
                        continue; // no location info at all — can't save
                    }

                    RoadHazard hazard = new RoadHazard(HazardType.POTHOLE, hazardLat, hazardLng, severity, confidence);
                    if (startLabel != null && endLabel != null) {
                        hazard.setAddress(startLabel + " -> " + endLabel);
                    }
                    savedHazards.add(hazardRepository.save(hazard));
                }
            }
        }

        return Map.of(
                "detectionResult", response == null ? Map.of() : response,
                "savedHazards", savedHazards
        );
    }

    private List<LatLng> parseRoute(String routePointsJson) throws IOException {
        if (routePointsJson == null || routePointsJson.isBlank()) return null;
        LatLng[] arr = objectMapper.readValue(routePointsJson, LatLng[].class);
        List<LatLng> list = new ArrayList<>();
        for (LatLng p : arr) list.add(p);
        return list;
    }

    /** Interpolates a lat/lng at a given cumulative distance along the route polyline. */
    private double[] pointAtDistance(List<LatLng> route, double[] cumulative, double targetDist) {
        int i = 0;
        while (i < cumulative.length - 1 && cumulative[i + 1] < targetDist) i++;
        if (i >= route.size() - 1) {
            LatLng last = route.get(route.size() - 1);
            return new double[]{last.lat, last.lng};
        }
        LatLng a = route.get(i), b = route.get(i + 1);
        double segLen = cumulative[i + 1] - cumulative[i];
        double t = segLen == 0 ? 0 : (targetDist - cumulative[i]) / segLen;
        return new double[]{a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t};
    }

    @DeleteMapping("/api/hazards/all")
    public Map<String, Object> clearAll() {
        long count = hazardRepository.count();
        hazardRepository.deleteAll();
        return Map.of("deleted", count);
    }
}