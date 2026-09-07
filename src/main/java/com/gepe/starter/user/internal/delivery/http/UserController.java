package com.gepe.starter.user.internal.delivery.http;

import java.util.List;
import java.util.UUID;

import com.gepe.starter.platform.i18n.MessageHelper;
import com.gepe.starter.platform.web.response.ApiResponse;
import com.gepe.starter.user.api.UserApi;
import com.gepe.starter.user.api.dto.CreateUserCommand;
import com.gepe.starter.user.api.dto.UserResponse;
import com.gepe.starter.user.internal.delivery.http.req.CreateUserRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * HTTP layer of the {@code user} module (see {@code agents.md} §2.2): thin
 * adapter between {@code req} records and the module facade ({@code UserApi});
 * every body is a platform envelope. All error bodies are produced centrally
 * by {@code GlobalExceptionHandler} — modules never define their own advice.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApi userApi;
    private final MessageHelper messages;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userApi.createUser(new CreateUserCommand(request.name(), request.email()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(messages.get("user.created"), user));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable UUID id) {
        return new ApiResponse<>(null, userApi.getUser(id));
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> getUsers() {
        return new ApiResponse<>(null, userApi.getUsers());
    }
}
