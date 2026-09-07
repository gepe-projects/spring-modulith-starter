package com.gepe.starter.user.internal.listener;

import com.gepe.starter.user.api.event.UserCreatedEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@link UserCreatedEvent} after the creating transaction committed
 * (see {@code agents.md} §3: async/decoupled consumers listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} in their own
 * {@code internal/listener}).
 *
 * <p>This is the demo stand-in for a real side effect — welcome email,
 * notification, a call into another module's facade, … In a real module you
 * would call that code here instead of just logging.
 *
 * <p><strong>Multi-instance note</strong> ({@code agents.md} §11): the Modulith
 * event publication registry writes one entry per listener in the publishing
 * transaction and completes it after this method returns. If the instance
 * crashes in between, another instance's staleness monitor / restart
 * republishing re-delivers the event. Delivery is therefore <em>at-least-once
 * and possibly duplicated</em> — keep listeners idempotent (this log-only
 * listener trivially is).
 */
@Component
@Slf4j
public class UserEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        log.info("User created event received after commit: userId={}", event.userId());
    }
}
