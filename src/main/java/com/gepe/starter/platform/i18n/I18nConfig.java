package com.gepe.starter.platform.i18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Single aggregated {@link MessageSource} for the whole application (see §6 of
 * {@code agents.md}).
 *
 * <p>Registers one basename per folder found under the classpath directory
 * {@code i18n} — {@code i18n/<folder>/messages} — so the app-wide
 * {@code i18n/messages/} bundle and every module bundle ({@code i18n/user/},
 * {@code i18n/order/}, …) are resolved through this one bean. Adding a module
 * bundle therefore needs no configuration change.
 *
 * <p>The bean deliberately uses the conventional name
 * {@link AbstractApplicationContext#MESSAGE_SOURCE_BEAN_NAME "messageSource"} so
 * Boot's {@code MessageSourceAutoConfiguration} backs off. Being the context's
 * {@code messageSource} also makes Spring Boot's auto-configured validator
 * interpolate constraint messages ({@code jakarta.validation.constraints.…},
 * {@code {module.key}}) through this source with the request locale — see
 * {@code agents.md} §6.
 *
 * <p>Default locale is English; unknown locales fall back to the default
 * (English) bundle instead of the system locale.
 */
@Configuration
public class I18nConfig {

    /** Default-bundle file scanned per i18n folder (convention §6 of {@code agents.md}). */
    static final String FOLDER_PATTERN = "classpath*:i18n/*/messages.properties";

    /**
     * Discovers every i18n folder on the classpath and returns one basename per
     * folder: global {@code i18n/messages/messages} first, then module folders in
     * alphabetical order.
     */
    static List<String> discoverBasenames() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(FOLDER_PATTERN);

        Set<String> folders = new TreeSet<>();
        for (Resource resource : resources) {
            folderOf(resource).ifPresent(folders::add);
        }

        return folders.stream()
                .sorted(Comparator.comparing((String folder) -> !folder.equals("messages"))
                        .thenComparing(folder -> folder))
                .map(folder -> "i18n/" + folder + "/messages")
                .toList();
    }

    private static java.util.Optional<String> folderOf(Resource resource) throws IOException {
        // URL ends with ".../i18n/<folder>/messages.properties"; the folder has
        // exactly one path segment (the pattern guarantees it).
        String url = resource.getURL().toString();
        int marker = url.lastIndexOf("/i18n/");
        if (marker < 0) {
            return java.util.Optional.empty();
        }
        String after = url.substring(marker + "/i18n/".length());
        int end = after.lastIndexOf("/messages.properties");
        if (end <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(after.substring(0, end));
    }

    @Bean(name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
    public MessageSource messageSource() throws IOException {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(discoverBasenames().toArray(new String[0]));
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setDefaultLocale(Locale.ENGLISH);
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }
}
