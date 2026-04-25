package dev.bored.stream.repository;

import dev.bored.stream.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice tests for {@link AppUserRepository} using the default H2 in-memory
 * DB. Exercises both the JPA mapping (via save/find round-trips) and every
 * custom derived query declared on the interface.
 */
@DataJpaTest
class AppUserRepositoryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    private AppUser buildUser(String handle, String displayName) {
        AppUser u = new AppUser();
        u.setUserId(UUID.randomUUID());
        u.setHandle(handle);
        u.setDisplayName(displayName);
        u.setAvatarUrl("https://example.com/avatar.png");
        return u;
    }

    @Test
    void save_andFindById_roundTripsAllFields() {
        AppUser saved = appUserRepository.saveAndFlush(buildUser("alice", "Alice"));

        Optional<AppUser> found = appUserRepository.findById(saved.getUserId());

        assertThat(found).isPresent();
        assertThat(found.get().getHandle()).isEqualTo("alice");
        assertThat(found.get().getDisplayName()).isEqualTo("Alice");
        assertThat(found.get().getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void findByHandle_returnsUser_whenPresent() {
        appUserRepository.saveAndFlush(buildUser("bob", "Bob"));

        Optional<AppUser> found = appUserRepository.findByHandle("bob");

        assertThat(found).isPresent();
        assertThat(found.get().getHandle()).isEqualTo("bob");
    }

    @Test
    void findByHandle_returnsEmpty_whenMissing() {
        Optional<AppUser> found = appUserRepository.findByHandle("nobody");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByHandle_reflectsPresence() {
        appUserRepository.saveAndFlush(buildUser("carol", "Carol"));

        assertThat(appUserRepository.existsByHandle("carol")).isTrue();
        assertThat(appUserRepository.existsByHandle("missing")).isFalse();
    }
}
