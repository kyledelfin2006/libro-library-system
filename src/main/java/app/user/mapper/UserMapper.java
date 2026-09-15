package app.user.mapper;

import app.user.dto.UserCreateRequestDTO;
import app.user.dto.UserResponseDTO;
import app.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Converts user entities to API DTOs and create requests to entities.
 *
 * <p>The mapper deliberately accepts an already encoded password hash instead
 * of encoding the request password. Password encoding belongs to the service
 * layer, where the user creation business flow is coordinated.</p>
 */
@Component
public class UserMapper {

    /**
     * Converts a {@link User} to a response DTO.
     * Password fields are intentionally not included in the response DTO.
     *
     * @param user the entity to convert
     * @return the response DTO, or {@code null} when the entity is null
     */
    public UserResponseDTO toResponseDTO(User user) {
        if (user == null) {
            return null;
        }

        return new UserResponseDTO(
                user.getUniversityId(),
                user.getFirstName(),
                user.getLastName(),
                user.getMiddleInitial(),
                user.getEmail(),
                user.getUserRole(),
                user.getUserCourse(),
                user.getMajor()
        );
    }

    /**
     * Converts a list of user entities to response DTOs.
     *
     * @param users the entities to convert
     * @return response DTOs, or an empty list when the input is null or empty
     */
    public List<UserResponseDTO> toResponseDTOList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }

        return users.stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /**
     * Converts a create request to a new entity.
     *
     * @param request the create request
     * @param encodedPasswordHash the hash produced by PasswordEncoder
     * @return a new user entity, or {@code null} when the request is null
     */
    public User toEntity(UserCreateRequestDTO request, String encodedPasswordHash) {
        if (request == null) {
            return null;
        }

        User user = new User();
        user.setUniversityId(request.getUniversityId());
        user.setPasswordHash(encodedPasswordHash);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setMiddleInitial(request.getMiddleInitial());
        user.setEmail(request.getEmail());
        user.setUserRole(request.getUserRole());
        user.setUserCourse(request.getUserCourse());
        user.setMajor(request.getMajor());
        return user;
    }
}
