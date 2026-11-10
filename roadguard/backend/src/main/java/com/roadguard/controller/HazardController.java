package com.roadguard.controller;

import com.roadguard.entity.HazardStatus;
import com.roadguard.entity.RoadHazard;
import com.roadguard.repository.RoadHazardRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/hazards")
public class HazardController {

    private final RoadHazardRepository hazardRepository;

    public HazardController(RoadHazardRepository hazardRepository) {
        this.hazardRepository = hazardRepository;
    }

    @GetMapping
    public List<RoadHazard> list() {
        return hazardRepository.findAll();
    }

    @PostMapping
    public RoadHazard create(@RequestBody RoadHazard hazard) {
        hazard.setId(null); // ensure it's treated as new
        return hazardRepository.save(hazard);
    }

    /** Move a hazard through ACTIVE -> UNDER_REPAIR -> FIXED (municipality dashboard action). */
    @PatchMapping("/{id}/status")
    public RoadHazard updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        RoadHazard hazard = hazardRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Hazard not found: " + id));
        hazard.setStatus(HazardStatus.valueOf(body.get("status")));
        return hazardRepository.save(hazard);
    }

    /** A citizen confirming an existing report bumps its count instead of creating a duplicate. */
    @PostMapping("/{id}/confirm")
    public RoadHazard confirm(@PathVariable Long id) {
        RoadHazard hazard = hazardRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Hazard not found: " + id));
        hazard.setReportCount(hazard.getReportCount() + 1);
        if (hazard.getReportCount() >= 3) {
            hazard.setVerified(true);
        }
        return hazardRepository.save(hazard);
    }
}
