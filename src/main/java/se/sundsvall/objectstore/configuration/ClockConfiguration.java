package se.sundsvall.objectstore.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfiguration {

	/**
	 * The source of the current time, injected rather than read from the static factory methods so that the zone it is
	 * read in is explicit and so that a test can fix it. The default zone of the JVM is the one that was in use before
	 * the clock was made explicit, and expiry is compared as an instant either way, so the zone only decides the offset
	 * a timestamp carries on its way out.
	 *
	 * @return the clock the service reads the current time from
	 */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}
}
