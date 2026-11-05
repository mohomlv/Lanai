package com.landata.query.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleParameterQuestionRepository extends JpaRepository<RoleParameterQuestion, UUID> {
  List<RoleParameterQuestion> findByCompanyIdAndRole(UUID companyId, String role);

  List<RoleParameterQuestion> findByCompanyId(UUID companyId);

  boolean existsByCompanyIdAndRoleAndQuestionId(UUID companyId, String role, UUID questionId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from RoleParameterQuestion item where item.companyId = :companyId")
  void deleteByCompanyId(@Param("companyId") UUID companyId);
}
