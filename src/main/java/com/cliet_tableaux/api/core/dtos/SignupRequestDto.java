package com.cliet_tableaux.api.core.dtos;

// DTO d'entrée dédié à l'inscription : ne porte ni id ni admin, pour qu'aucun client ne puisse
// piloter l'identité de l'entité créée (écrasement d'un compte existant) ni s'auto-attribuer
// le rôle admin. Voir AUDIT_BACKEND.md, findings #1 et #2.
public record SignupRequestDto(String email, String password, String firstName, String lastName) {
}
