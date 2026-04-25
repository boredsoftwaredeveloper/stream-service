package dev.bored.stream.repository;

import dev.bored.stream.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AppUser} entities.
 *
 * <p>Primary lookup is by {@code user_id} (the Supabase JWT {@code sub}).
 * Handle lookup is exposed for the UNIQUE-constraint check performed
 * when generating a new user's handle at first-write time.</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-19
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Looks up a user by their URL-safe handle.
     *
     * @param handle the unique, lowercased handle
     * @return the matching user, or empty if none exists
     */
    Optional<AppUser> findByHandle(String handle);

    /**
     * Returns {@code true} if any user currently holds this handle.
     * Used when generating candidate handles to find an unused one.
     *
     * @param handle the candidate handle
     * @return whether the handle is already taken
     */
    boolean existsByHandle(String handle);
}
