package com.roadguard.repository;

import com.roadguard.entity.HazardStatus;
import com.roadguard.entity.RoadHazard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoadHazardRepository extends JpaRepository<RoadHazard, Long> {
    List<RoadHazard> findByStatus(HazardStatus status);
}
