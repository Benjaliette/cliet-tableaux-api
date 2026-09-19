package com.cliet_tableaux.api.core.mappers;

import com.cliet_tableaux.api.core.dtos.SignupRequestDto;
import com.cliet_tableaux.api.core.dtos.UserResponseDto;
import com.cliet_tableaux.api.core.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    /**
     * Convertit une entité User en DTO de sortie UserResponseDto.
     *
     * @param user L'entité {@link User}
     * @return Le DTO de sortie correspondant (jamais de password)
     */
    UserResponseDto toDto(User user);

    /**
     * Convertit un DTO d'inscription en entité User
     *
     * @param signupRequestDto Le DTO {@link SignupRequestDto}
     * @return L'entité JPA correspondante
     */
    User toEntity(SignupRequestDto signupRequestDto);
}
