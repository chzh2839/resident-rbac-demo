package com.example.rbac.repository;

import com.example.rbac.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 최신순 정렬. createdAt 동률 시 id 로 2차 정렬해 삽입 순서를 보장한다. */
    List<AuditLog> findAllByOrderByCreatedAtDescIdDesc();
}
