package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long>, JpaSpecificationExecutor<LoginHistory> {

    Page<LoginHistory> findByUserId(Long userId, Pageable pageable);

    Page<LoginHistory> findByEmailIgnoreCase(String email, Pageable pageable);

    long countByUserIdAndStatus(Long userId, String status);

    long countByEmailIgnoreCaseAndStatus(String email, String status);

    List<LoginHistory> findTop10ByUserIdOrderByLoginTimeDesc(Long userId);

    void deleteByUserId(Long userId);

    void deleteByEmail(String email);
}
