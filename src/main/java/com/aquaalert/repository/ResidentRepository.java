package com.aquaalert.repository;

import com.aquaalert.entity.Resident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    boolean existsByResidentCode(String residentCode);

    boolean existsByPhoneNumberAndAreaId(String phoneNumber, Long areaId);

    Optional<Resident> findByResidentCode(String residentCode);

    List<Resident> findAllByAreaIdAndStatus(Long areaId, com.aquaalert.entity.ResidentStatus status);

    long countByStatus(com.aquaalert.entity.ResidentStatus status);

    long countByStatusAndAreaWardIgnoreCase(com.aquaalert.entity.ResidentStatus status, String ward);

    @Query("""
           SELECT r FROM Resident r
           WHERE (:search IS NULL OR :search = '' OR
                  LOWER(r.ownerName)   LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(COALESCE(r.email, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(r.houseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR
                  r.phoneNumber LIKE CONCAT('%', :search, '%') OR
                  LOWER(r.residentCode) LIKE LOWER(CONCAT('%', :search, '%')))
           AND (:areaId IS NULL OR r.area.id = :areaId)
           AND (:status IS NULL OR r.status = :status)
           AND (:ward IS NULL OR :ward = '' OR LOWER(r.area.ward) = LOWER(:ward))
           """)
    Page<Resident> search(@Param("search") String search,
                           @Param("areaId") Long areaId,
                           @Param("status") com.aquaalert.entity.ResidentStatus status,
                           @Param("ward") String ward,
                           Pageable pageable);

    // Used by CSV export — streams every matching row rather than paging,
    // since an export needs the full result set, not one page of it.
    @Query("""
           SELECT r FROM Resident r
           WHERE (:areaId IS NULL OR r.area.id = :areaId)
           AND (:status IS NULL OR r.status = :status)
           AND (:ward IS NULL OR :ward = '' OR LOWER(r.area.ward) = LOWER(:ward))
           ORDER BY r.area.name, r.houseNumber
           """)
    List<Resident> findAllForExport(@Param("areaId") Long areaId,
                                     @Param("status") com.aquaalert.entity.ResidentStatus status,
                                     @Param("ward") String ward);
}
