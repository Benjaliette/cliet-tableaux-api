package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.dtos.ChangePasswordRequestDto;
import com.cliet_tableaux.api.core.dtos.UpdateProfileRequestDto;
import com.cliet_tableaux.api.core.dtos.UserResponseDto;
import com.cliet_tableaux.api.core.exceptions.AuthenticationException;
import com.cliet_tableaux.api.core.exceptions.ResourceNotFoundException;
import com.cliet_tableaux.api.core.mappers.UserMapper;
import com.cliet_tableaux.api.core.model.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService implements UserDetailsService {
    private final UserDao userDao;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userDao.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }

    public List<UserResponseDto> findAll() {
        return userDao.findAll().stream().map(userMapper::toDto).toList();
    }

    public boolean existsByEmail(String email) {
        return userDao.existsByEmail(email);
    }

    public User findUserByRefreshToken(final String refreshToken) {
        return userDao.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ResourceNotFoundException("User not found my its refresh token"));
    }

    public User save(User user) {
        return userDao.save(user);
    }

    public UserResponseDto getCurrentUserProfile(User currentUser) {
        return userMapper.toDto(currentUser);
    }

    public UserResponseDto updateProfile(User currentUser, UpdateProfileRequestDto request) {
        currentUser.setFirstName(request.firstName());
        currentUser.setLastName(request.lastName());
        return userMapper.toDto(save(currentUser));
    }

    public void changePassword(User currentUser, ChangePasswordRequestDto request) {
        if (!passwordEncoder.matches(request.currentPassword(), currentUser.getPassword())) {
            throw new AuthenticationException("Mot de passe actuel incorrect");
        }

        currentUser.setPassword(passwordEncoder.encode(request.newPassword()));
        save(currentUser);
    }
}
