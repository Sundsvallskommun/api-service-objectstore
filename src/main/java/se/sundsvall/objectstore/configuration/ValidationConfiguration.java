package se.sundsvall.objectstore.configuration;

import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ValidationConfiguration {

	/**
	 * Points bean validation at the same clock the rest of the service reads, so that a constraint deciding what is in
	 * the future decides it from the injected clock rather than from the wall clock of the JVM. Without this the clock
	 * is the source of time everywhere except in validation, and a test that fixes it fixes it everywhere except there.
	 *
	 * @param  clock the clock the service reads the current time from
	 * @return       the customizer handing that clock to the validator
	 */
	@Bean
	ValidationConfigurationCustomizer validationClockCustomizer(final Clock clock) {
		return configuration -> configuration.clockProvider(() -> clock);
	}
}
