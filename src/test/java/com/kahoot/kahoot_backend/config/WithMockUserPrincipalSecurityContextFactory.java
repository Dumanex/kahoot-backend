package com.kahoot.kahoot_backend.config;

import com.kahoot.kahoot_backend.model.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockUserPrincipalSecurityContextFactory implements WithSecurityContextFactory<WithMockUserPrincipal> {
    @Override
    public SecurityContext createSecurityContext(WithMockUserPrincipal annotation) {
        User user = User.builder()
                .id(annotation.id())
                .username(annotation.username())
                .build();

        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        return context;
    }
}
