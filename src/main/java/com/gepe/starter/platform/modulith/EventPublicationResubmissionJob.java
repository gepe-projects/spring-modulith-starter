package com.gepe.starter.platform.modulith;

import java.time.Duration;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.quartz.QuartzJobBean;

import lombok.extern.slf4j.Slf4j;

/**
 * Resubmits FAILED event publications (see {@code agents.md} §11.1).
 *
 * <p>Complements the staleness monitor + restart republishing: publications
 * that were marked FAILED (listener crash, staleness) are re-delivered to
 * their listeners on a schedule instead of waiting for the next restart.
 * Runs as a clustered Quartz job (single execution across instances), every
 * {@value EventPublicationResubmissionScheduler#RESUBMISSION_INTERVAL_MINUTES}
 * minutes.
 *
 * <p>Guard rails against infinite retries / overload:
 * <ul>
 *   <li>{@link #MAX_ATTEMPTS} — a publication is skipped once its listener ran
 *       that many times;</li>
 *   <li>{@code minAge} — only publications failed longer than 1 minute;</li>
 *   <li>{@code batchSize}/{@code maxInFlight} — bounded work per run.</li>
 * </ul>
 *
 * <p>Resubmission redelivers the event, so listeners must stay idempotent
 * (at-least-once semantics, {@code agents.md} §11).
 */
@Slf4j
@DisallowConcurrentExecution
class EventPublicationResubmissionJob extends QuartzJobBean {

    static final int MAX_ATTEMPTS = 10;

    private final FailedEventPublications failedEventPublications;

    EventPublicationResubmissionJob(FailedEventPublications failedEventPublications) {
        this.failedEventPublications = failedEventPublications;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.debug("Resubmitting FAILED event publications (max attempts {})", MAX_ATTEMPTS);
        failedEventPublications.resubmit(ResubmissionOptions.defaults()
                .withMaxInFlight(20)
                .withBatchSize(50)
                .withMinAge(Duration.ofMinutes(1))
                .withFilter(p -> p.getCompletionAttempts() < MAX_ATTEMPTS));
    }
}
