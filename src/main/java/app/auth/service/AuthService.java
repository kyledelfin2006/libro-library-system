package app.auth.service;

import app.auth.config.JwtProperties;
import app.auth.dto.LoginRequestDTO;
import app.auth.dto.TokenResponseDTO;
import app.user.entity.enums.UserRole;
import app.user.validation.PasswordPolicy;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Authenticates submitted credentials through Spring Security and issues a local access token. */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    /**
     * Creates the login service.
     *
     * @param authenticationManager existing user lookup and BCrypt authentication provider
     * @param jwtEncoder Nimbus-backed signer configured with the deployment key
     * @param jwtProperties trusted issuer, audience, and token lifetime
     */
    public AuthService(AuthenticationManager authenticationManager,
                       JwtEncoder jwtEncoder,
                       JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Verifies credentials and signs a token containing only the authenticated identity and role.
     *
     * @param request validated login credentials
     * @return a Bearer token with a lifetime no longer than the configured 15-minute maximum
     */
    public TokenResponseDTO login(LoginRequestDTO request) {
        // BCrypt has a 72-byte input boundary; reject multibyte overlength input before password work.
        if (!PasswordPolicy.isWithinBcryptByteLimit(request.getPassword())) {
            throw new BadCredentialsException("University ID or password is incorrect");
        }

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.getUniversityId(), request.getPassword()));
        String role = authenticatedLibroRole(authentication);
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.accessTokenTtlSeconds());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(authentication.getName())
                .audience(List.of(jwtProperties.audience()))
                .claim("roles", List.of(role))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        // The configured encoder and this header both pin the supported algorithm to HS256.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponseDTO(token, "Bearer", jwtProperties.accessTokenTtlSeconds());
    }

    /**
     * Extracts exactly one supported Libro role from the authenticated principal.
     *
     * @param authentication successful result returned by Spring Security's manager
     * @return the enum role name for the signed token claim
     */
    private String authenticatedLibroRole(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .toList();

        if (roles.size() != 1 || Arrays.stream(UserRole.values())
                .noneMatch(role -> role.name().equals(roles.getFirst()))) {
            throw new AuthenticationServiceException("Authenticated account has invalid role configuration");
        }
        return roles.getFirst();
    }
}
