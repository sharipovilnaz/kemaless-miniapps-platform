package com.kemaless.core.repository;

import com.kemaless.core.model.MiniApp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MiniAppRepository extends JpaRepository<MiniApp, UUID> {
    boolean existsByCode(String code);

    Optional<MiniApp> findByCode(String code);

    @Query("""
        SELECT m FROM MiniApp m 
        WHERE (:search IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) 
                               OR LOWER(m.code) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(m.description) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    Page<MiniApp> findAllWithFilters(@Param("search") String search, Pageable pageable);

}
