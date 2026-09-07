package com.gepe.starter.platform.i18n;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Tiny convenience over the aggregated {@link MessageSource} for code that only
 * has a message key (controllers, {@code GlobalExceptionHandler}, listeners).
 *
 * <p>Resolves against the locale of the current request
 * ({@link LocaleContextHolder}); when no locale is bound (non-web code) the
 * aggregated source falls back to the configured default locale (English). A
 * missing key never throws — it is logged and the key itself is returned,
 * which surfaces typos quickly during development (see §6 of {@code agents.md}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageHelper {

    private final MessageSource messageSource;

    /** Resolves {@code code} for the current request locale. */
    public String get(String code, Object... args) {
        var locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            log.warn("Message key '{}' not found for locale '{}'; returning the key as message", code, locale);
            return code;
        }
    }

    /**
     * Resolves a Spring {@link MessageSourceResolvable} (e.g. the per-parameter
     * errors of {@code HandlerMethodValidationException}) for the current
     * request locale. Falls back to the resolvable's default message and then
     * to its first code, warning on the way, so a missing key never breaks the
     * response.
     */
    public String get(MessageSourceResolvable resolvable) {
        var locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(resolvable, locale);
        } catch (NoSuchMessageException e) {
            String fallback = resolvable.getDefaultMessage();
            if (fallback != null) {
                return fallback;
            }
            String[] codes = resolvable.getCodes();
            String key = codes == null || codes.length == 0 ? "<no code>" : codes[0];
            log.warn("Message resolvable '{}' not found for locale '{}'; returning its code as message", key, locale);
            return key;
        }
    }
}
