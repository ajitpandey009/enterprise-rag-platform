package com.enterprise.rag.repository;

import com.enterprise.rag.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.tenant.id = :tenantId")
    Optional<User> findByUsernameAndTenantId(String username, UUID tenantId);

    boolean existsByUsername(String username);

    List<User> findByTenantId(UUID tenantId);
}
