package com.aquaalert.repository;

import com.aquaalert.entity.Area;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Long> {

    boolean existsByNameIgnoreCaseAndWardIgnoreCase(String name, String ward);

    long countByActiveTrue();

    long countByActiveTrueAndWardIgnoreCase(String ward);

    Optional<Area> findByIdAndActiveTrue(Long id);

    // Free-text search across name/ward/zone/street, case-insensitive.
    // Kept as one query (rather than JPA Specifications) since the
    // filter set here is small and unlikely to grow much.
    @Query("""
           SELECT a FROM Area a
           WHERE (:search IS NULL OR :search = '' OR
                  LOWER(a.name)   LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(a.ward)   LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(a.zone)   LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(a.street) LIKE LOWER(CONCAT('%', :search, '%')))
           AND (:activeOnly = FALSE OR a.active = TRUE)
           AND (:ward IS NULL OR :ward = '' OR LOWER(a.ward) = LOWER(:ward))
           """)
    Page<Area> search(@Param("search") String search,
                       @Param("activeOnly") boolean activeOnly,
                       @Param("ward") String ward,
                       Pageable pageable);

    // Now backed by the Resident entity (introduced in the Resident
    // Management module) instead of a native query.
    @Query("SELECT COUNT(r) FROM Resident r WHERE r.area.id = :areaId")
    long countResidentsByAreaId(@Param("areaId") Long areaId);
}
