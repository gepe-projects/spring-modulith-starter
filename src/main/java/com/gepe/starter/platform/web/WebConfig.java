package com.gepe.starter.platform.web;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Web configuration of the {@code platform} module (see §2.4 of {@code agents.md}).
 *
 * <p>Locale policy (see §6): the language comes from the {@code Accept-Language}
 * header restricted to the configured supported locales; the default is English,
 * so a missing or unsupported header never falls back to the server/system
 * locale. Extend {@code supportedLocales} only together with the matching
 * {@code messages_<lang>.properties} bundles — for every module bundle, not
 * just the app-wide one (a key without translation falls back to English).
 */
@Configuration
public class WebConfig {

    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("id")));
        return resolver;
    }
}
