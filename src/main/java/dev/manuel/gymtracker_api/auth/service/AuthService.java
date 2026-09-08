package dev.manuel.gymtracker_api.auth.service;

import dev.manuel.gymtracker_api.auth.dto.AuthResponse;
import dev.manuel.gymtracker_api.auth.dto.LoginRequest;
import dev.manuel.gymtracker_api.auth.exception.InvalidCredentialsException;
import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        String accessToken =
                jwtService.generateToken(user.getId());

        return new AuthResponse(accessToken);
    }
}