package com.gepe.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

/**
 * Application entry point.
 *
 * <p>{@code @Modulith} (instead of plain {@code @SpringBootApplication})
 * registers {@code platform} as a <em>shared</em> module — usable by every
 * feature module without listing it in their {@code allowedDependencies} (see
 * {@code agents.md} §2.3/§2.4).
 */
@Modulith(sharedModules = "platform")
public class SpringModulithStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringModulithStarterApplication.class, args);
    }

}
