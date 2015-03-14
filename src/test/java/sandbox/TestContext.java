package sandbox;

import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@Configuration
@ComponentScan(
		basePackageClasses = FooController.class,
		useDefaultFilters = false,
		includeFilters = @ComponentScan.Filter(ControllerAdvice.class))
@Import(JacksonAutoConfiguration.class)
public class TestContext extends WebMvcConfigurationSupport {
	@Bean
	public TestHelper testHelper() {
		return new TestHelper();
	}
}
