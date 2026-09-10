package com.kahoot.kahoot_backend.config;

import com.kahoot.kahoot_backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class UserPrincipalTest {
    private User user;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("player1")
                .email("player1@test.com")
                .passwordHash("hashedPassword")
                .build();

        userPrincipal = new UserPrincipal(user);
    }

    @Test
    void getUsername_shouldReturnUsernameFromUser() {
        String username = userPrincipal.getUsername();

        assertThat(username).isEqualTo("player1");
    }

    @Test
    void getPassword_shouldReturnPasswordFromUser() {
        String password = userPrincipal.getPassword();

        assertThat(password).isEqualTo("hashedPassword");
    }

    @Test
    void getAuthorities_shouldReturnSingleRoleUser() {
        Collection<? extends GrantedAuthority> authorities = userPrincipal.getAuthorities();

        assertThat(authorities).hasSize(1);
        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void isAccountNonExpired_shouldReturnTrue() {
        boolean result = userPrincipal.isAccountNonExpired();

        assertThat(result).isTrue();
    }

    @Test
    void isAccountNonLocked_shouldReturnTrue() {
        boolean result = userPrincipal.isAccountNonLocked();

        assertThat(result).isTrue();
    }

    @Test
    void isCredentialsNonExpired_shouldReturnTrue() {
        boolean result = userPrincipal.isCredentialsNonExpired();

        assertThat(result).isTrue();
    }

    @Test
    void isEnabled_shouldReturnTrue() {
        boolean result = userPrincipal.isEnabled();

        assertThat(result).isTrue();
    }

    @Test
    void getUser_shouldReturnOriginalUserObject() {
        User result = userPrincipal.getUser();

        assertThat(result).isSameAs(user);
    }
}
