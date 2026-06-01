package com.kemaless.core.model;

import com.kemaless.core.enums.AccessType;
import com.kemaless.core.enums.RoleMatchMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessPolicy {

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    private AccessType accessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_match_mode")
    private RoleMatchMode roleMatchMode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "realm_miniapp_roles",
            joinColumns = @JoinColumn(name = "realm_miniapp_id")
    )
    @Column(name = "role_code")
    private Set<String> requiredRoles;
}
