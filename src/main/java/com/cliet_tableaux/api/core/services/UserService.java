package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.dtos.UserDto;
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

    public List<UserDto> findAll() {
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
}
