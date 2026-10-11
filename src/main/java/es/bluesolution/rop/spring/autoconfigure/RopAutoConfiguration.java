package es.bluesolution.rop.spring.autoconfigure;

import es.bluesolution.rop.spring.ErrorMessageFormatter;
import es.bluesolution.rop.spring.RailwayErrorResponseBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Railway-Oriented Programming error handling.
 *
 * <p>Registered through {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * so consumers get the beans without component-scanning {@code es.bluesolution.rop}. Sets up the
 * {@link ErrorMessageFormatter} (mode from {@code railway.error.response.mode}, default {@code PRODUCTION})
 * and wires it into the static {@link RailwayErrorResponseBuilder}.</p>
 */
@AutoConfiguration
public class RopAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ErrorMessageFormatter errorMessageFormatter(
            @Value("${railway.error.response.mode:PRODUCTION}") String errorMode) {
        return new ErrorMessageFormatter(errorMode);
    }

    @Bean
    @ConditionalOnMissingBean
    public RailwayErrorResponseBuilderConfigurer railwayErrorResponseBuilderConfigurer(
            ErrorMessageFormatter errorMessageFormatter) {
        RailwayErrorResponseBuilder.setErrorMessageFormatter(errorMessageFormatter);
        return new RailwayErrorResponseBuilderConfigurer();
    }

    /**
     * Marker bean proving the formatter has been wired into {@link RailwayErrorResponseBuilder}.
     */
    public static class RailwayErrorResponseBuilderConfigurer {
    }
}
