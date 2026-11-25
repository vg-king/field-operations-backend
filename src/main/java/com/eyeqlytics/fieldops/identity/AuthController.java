package com.eyeqlytics.fieldops.identity;

import com.eyeqlytics.fieldops.common.ApiException;
import com.eyeqlytics.fieldops.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank String fullName, @NotBlank @Email String email,
                                  String phone, @NotBlank @Size(min = 8) String password,
                                  @NotNull Long zoneId) {}
    public record TokenResponse(String token, Long userId, String fullName, Role role, Long zoneId) {}

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        User u = users.findByEmail(req.email())
                .filter(User::isActive)
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password"));
        return new TokenResponse(jwt.generate(u), u.getId(), u.getFullName(), u.getRole(), u.getZoneId());
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByEmail(req.email()))
            throw ApiException.conflict("EMAIL_TAKEN", "Email already registered");
        User u = new User();
        u.setFullName(req.fullName());
        u.setEmail(req.email());
        u.setPhone(req.phone());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.REQUESTER);
        u.setZoneId(req.zoneId());
        users.save(u);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TokenResponse(jwt.generate(u), u.getId(), u.getFullName(), u.getRole(), u.getZoneId()));
    }
}
