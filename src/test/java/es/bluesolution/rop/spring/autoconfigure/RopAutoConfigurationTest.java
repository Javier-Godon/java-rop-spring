package es.bluesolution.rop.spring.autoconfigure;

import es.bluesolution.rop.spring.ErrorMessageFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RopAutoConfiguration")
class RopAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RopAutoConfiguration.class));

    @Test
    @DisplayName("registers the formatter and the builder configurer with PRODUCTION mode by default")
    void registersBeansWithDefaults() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ErrorMessageFormatter.class);
            assertThat(context).hasSingleBean(RopAutoConfiguration.RailwayErrorResponseBuilderConfigurer.class);
            ErrorMessageFormatter formatter = context.getBean(ErrorMessageFormatter.class);
            assertThat(formatter.shouldIncludeExceptionDetails()).isFalse();
            assertThat(formatter.shouldIncludeStackTrace()).isFalse();
        });
    }

    @Test
    @DisplayName("honours railway.error.response.mode")
    void honoursConfiguredMode() {
        runner.withPropertyValues("railway.error.response.mode=development").run(context -> {
            ErrorMessageFormatter formatter = context.getBean(ErrorMessageFormatter.class);
            assertThat(formatter.shouldIncludeStackTrace()).isTrue();
        });
    }

    @Test
    @DisplayName("backs off when the application defines its own formatter")
    void backsOffForUserFormatter() {
        ErrorMessageFormatter custom = new ErrorMessageFormatter("DETAILED");
        runner.withBean(ErrorMessageFormatter.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(ErrorMessageFormatter.class);
            assertThat(context.getBean(ErrorMessageFormatter.class)).isSameAs(custom);
        });
    }

    @Test
    @DisplayName("is registered in AutoConfiguration.imports")
    void isListedInImports() throws Exception {
        try (var in = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes())).contains(RopAutoConfiguration.class.getName());
        }
    }
}
