package se.sundsvall.objectstore.configuration;

import jakarta.validation.Validation;
import jakarta.validation.constraints.Future;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationConfigurationTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);

	/**
	 * A constraint deciding what is in the future has to decide it from the clock the service reads rather than from the
	 * wall clock of the JVM, so that fixing the clock fixes it in validation too. The clock is fixed at a point the wall
	 * clock has long passed, so only the customizer can tell the two points in time below apart.
	 */
	@Test
	void futureIsDecidedByTheInjectedClock() {
		// Arrange
		final var configuration = Validation.byDefaultProvider().configure();
		new ValidationConfiguration().validationClockCustomizer(CLOCK).customize(configuration);

		// Act & Assert
		try (final var factory = configuration.buildValidatorFactory()) {
			final var validator = factory.getValidator();

			assertThat(validator.validate(new Expiry(OffsetDateTime.parse("2026-08-21T12:00:00Z")))).isEmpty();
			assertThat(validator.validate(new Expiry(OffsetDateTime.parse("2026-08-19T12:00:00Z"))))
				.singleElement()
				.satisfies(violation -> assertThat(violation.getPropertyPath()).hasToString("expiresAt"));
		}
	}

	private record Expiry(@Future OffsetDateTime expiresAt) {}
}
