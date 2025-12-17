package com.cliet_tableaux.api.core.mappers;

import com.cliet_tableaux.api.core.dtos.UserDto;
import com.cliet_tableaux.api.core.model.User;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    /**
     * Convertit une entité User en DTO UserDto.
     *
     * @param user L'entité {@link User}
     * @return Le DTO correspondant
     */
    UserDto toDto(User user);

    /**
     * Convertit un DTO UserDto en entité User.
     *
     * @param userDto Le DTO {@link UserDto}
     * @return L'entité JPA correspondante
     */
    @InheritInverseConfiguration(name = "toDto")
    User toEntity(UserDto userDto);
}
