package com.medic.auth.infrastructure.rest;

import com.medic.auth.application.service.AuthService;
import com.medic.auth.domain.model.User;
import com.medic.auth.infrastructure.rest.dto.ErrorResponse;
import com.medic.auth.infrastructure.rest.dto.UpdateProfileRequest;
import com.medic.auth.infrastructure.rest.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User profile management — requires a valid JWT access token")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Get current user profile",
            description = "Returns the profile of the authenticated user derived from the JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(toResponse(user));
    }

    @Operation(summary = "Update current user profile",
            description = "Updates fullName and/or phone for the authenticated user. " +
                    "Null or blank fields are ignored — only provided values are applied.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated profile",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User current = authService.getUserByEmail(userDetails.getUsername());
        UserResponse updated = authService.updateProfile(current.getId(), request.fullName(), request.phone());
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get user by ID",
            description = "Returns the profile for the given user ID. " +
                    "PACIENTE role can only access their own profile; ADMIN and MEDICO can access any profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "PACIENTE attempting to access another user's profile",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "Target user ID") @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean isPrivileged = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_MEDICO".equals(a.getAuthority()));
        if (!isPrivileged) {
            User requester = authService.getUserByEmail(userDetails.getUsername());
            if (!requester.getId().equals(id)) {
                throw new AccessDeniedException("Access denied");
            }
        }
        User user = authService.getUserById(id);
        return ResponseEntity.ok(toResponse(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole().getName().name(),
                user.getActive(),
                user.getEmailVerified(),
                user.getCreatedAt()
        );
    }
}
