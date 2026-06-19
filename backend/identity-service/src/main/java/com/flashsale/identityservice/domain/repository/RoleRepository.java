package com.flashsale.identityservice.domain.repository;

import com.flashsale.identityservice.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findFirstByUserIdOrderByIdAsc(Long userId);
}

