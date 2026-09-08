package com.gepe.starter.platform.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Behavior tests for the aggregated {@link MessageSource} (see §6 and §8 of
 * {@code agents.md}): folder discovery, English resolution, argument
 * interpolation, fallback to English for unsupported locales, and the
 * {@link MessageHelper} fallback for missing keys.
 *
 * <p>The test classpath adds {@code src/test/resources/i18n/testmodule/} as a
 * second bundle, proving that a module folder is aggregated without any
 * configuration change.
 */
class MessageSourceTest {

    private static MessageSource messages;

    @BeforeAll
    static void buildAggregatedSource() throws IOException {
        messages = new I18nConfig().messageSource();
    }

    @Test
    void registersGlobalBundleFirstThenModuleBundlesAlphabetically() throws IOException {
        assertThat(I18nConfig.discoverBasenames())
                .containsExactly("i18n/messages/messages",
                        "i18n/testmodule/messages", // test fixture under src/test/resources
                        "i18n/user/messages");      // example feature module bundle
    }

    @Test
    void resolvesAppWideKeyInEnglish() {
        assertThat(messages.getMessage("common.success", null, Locale.ENGLISH)).isEqualTo("Success");
        assertThat(messages.getMessage("validation.failed", null, Locale.ENGLISH)).isEqualTo("Validation failed");
        assertThat(messages.getMessage("http.not_found", null, Locale.ENGLISH)).isEqualTo("Resource not found");
    }

    @Test
    void resolvesModuleKeyWithArguments() {
        assertThat(messages.getMessage("testmodule.not-found", new Object[] { "42" }, Locale.ENGLISH))
                .isEqualTo("User with id 42 was not found");
    }

    @Test
    void resolvesJakartaValidationKeysFromTheAppWideBundle() {
        assertThat(messages.getMessage("jakarta.validation.constraints.NotBlank.message", null, Locale.ENGLISH))
                .isEqualTo("must not be blank");
        assertThat(messages.getMessage("jakarta.validation.constraints.Email.message", null, Locale.ENGLISH))
                .isEqualTo("must be a well-formed email address");
    }

    @Test
    void resolvesIndonesianBundleForTheIdLocale() {
        assertThat(messages.getMessage("common.created", null, Locale.forLanguageTag("id")))
                .isEqualTo("Data berhasil dibuat");
    }

    @Test
    void fallsBackToEnglishForUnsupportedLocales() {
        for (Locale locale : List.of(Locale.GERMAN, Locale.FRENCH)) {
            assertThat(messages.getMessage("common.created", null, locale))
                    .as("resolution for %s", locale)
                    .isEqualTo("Data created successfully");
        }
    }

    @Test
    void messageHelperReturnsTheKeyWhenItIsMissing() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        try {
            MessageHelper helper = new MessageHelper(messages);
            assertThat(helper.get("no.such.key")).isEqualTo("no.such.key");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
