package com.roadguard.config;

import com.roadguard.entity.HazardType;
import com.roadguard.entity.RoadHazard;
import com.roadguard.entity.Severity;
import com.roadguard.repository.RoadHazardRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Inserts a handful of real Bengaluru-coordinate hazards on first boot so
 * the route-matching demo has data to find without requiring you to upload
 * photos first. Safe to run repeatedly — it only seeds when the table is empty.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final RoadHazardRepository hazardRepository;

    public DataSeeder(RoadHazardRepository hazardRepository) {
        this.hazardRepository = hazardRepository;
    }

    @Override
    public void run(String... args) {
        if (hazardRepository.count() > 0) return;

        // Points roughly along the Koramangala -> Whitefield corridor (Sarjapur / Marathahalli route)
        hazardRepository.save(new RoadHazard(HazardType.POTHOLE, 12.9352, 77.6146, Severity.HIGH, 0.91));
        hazardRepository.save(new RoadHazard(HazardType.SPEED_BREAKER, 12.9412, 77.6389, Severity.MEDIUM, 0.88));
        hazardRepository.save(new RoadHazard(HazardType.POTHOLE, 12.9500, 77.6650, Severity.MEDIUM, 0.79));
        hazardRepository.save(new RoadHazard(HazardType.POTHOLE, 12.9698, 77.7100, Severity.LOW, 0.72));
        hazardRepository.save(new RoadHazard(HazardType.ROAD_DAMAGE, 12.9750, 77.7300, Severity.CRITICAL, 0.95));
    }
}
