package com.ecomerceshopeonline.ecomercewepapp.entity.enums;

/**
 * Authority names. Kept with the {@code ROLE_} prefix so they can be handed to
 * Spring Security directly as {@code SimpleGrantedAuthority}.
 */
public enum RoleName {
    ROLE_CUSTOMER,
    ROLE_ADMIN
}
