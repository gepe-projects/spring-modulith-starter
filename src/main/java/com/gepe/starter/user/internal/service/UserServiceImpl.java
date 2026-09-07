package com.gepe.starter.user.internal.service;

import java.util.List;
import java.util.UUID;

import com.gepe.starter.platform.exception.ServiceException;
import com.gepe.starter.user.api.UserApi;
import com.gepe.starter.user.api.dto.CreateUserCommand;
import com.gepe.starter.user.api.dto.UserResponse;
import com.gepe.starter.user.api.event.UserCreatedEvent;
import com.gepe.starter.user.internal.entity.User;
import com.gepe.starter.user.internal.exception.UserError;
import com.gepe.starter.user.internal.repository.UserRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link UserApi} (see {@code agents.md} §2.1/§3): HTTP →
 * facade ({@code api}) → service → repository → entity. Logging follows §7
 * (Lombok {@code @Slf4j}, parameterized messages, business milestones at
 * {@code info}, expected misses at {@code debug}); the MDC {@code requestId}
 * set by {@code CorrelationIdFilter} is present on every line.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserApi {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserCommand command) {
        User user = User.create(command.name(), command.email());
        userRepository.save(user);
        // Force the INSERT now so a duplicate email fails deterministically
        // inside this transaction (constraint → DataIntegrityViolationException
        // → global HTTP 409), instead of at commit time.
        userRepository.flush();

        log.info("User created: id={}, email={}", user.getId(), user.getEmail());

        // Domain event: registered in event_publication within this transaction;
        // UserEventListener runs after commit and the registry completes the entry.
        events.publishEvent(new UserCreatedEvent(user.getId()));

        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> {
                    log.debug("User not found: id={}", id);
                    return new ServiceException(UserError.USER_NOT_FOUND, id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        log.debug("Loading all users");
        return userRepository.findAll(Sort.by("name")).stream()
                .map(this::toResponse)
                .toList();
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
