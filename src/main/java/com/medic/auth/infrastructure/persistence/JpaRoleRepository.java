package com.medic.auth.infrastructure.persistence;

import com.medic.auth.domain.model.Role;
import com.medic.auth.domain.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface JpaRoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(UserRole name);
}
