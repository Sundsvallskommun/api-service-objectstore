package se.sundsvall.objectstore.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(StoragePropertiesConfiguration.class);

	/**
	 * The maximum object size is the only bound on the memory an upload consumes and there is no value to fall back on,
	 * so a service configured without one refuses to start rather than starting and answering the first upload with an
	 * internal error.
	 */
	@Test
	void maxObjectSizeIsRequired() {
		contextRunner.run(context -> assertThat(context)
			.hasFailed()
			.getFailure()
			.hasStackTraceContaining("maxObjectSize"));
	}

	/**
	 * The time to live is genuinely optional — an object stored while it is unset never expires.
	 */
	@Test
	void defaultTimeToLiveIsOptional() {
		contextRunner
			.withPropertyValues("storage.max-object-size=15MB")
			.run(context -> assertThat(context)
				.hasNotFailed()
				.getBean(StorageProperties.class)
				.satisfies(properties -> {
					assertThat(properties.defaultTimeToLive()).isNull();
					assertThat(properties.maxObjectSize()).isEqualTo(DataSize.ofMegabytes(15));
				}));
	}

	@Test
	void bothPropertiesAreBound() {
		contextRunner
			.withPropertyValues("storage.max-object-size=15MB", "storage.default-time-to-live=P7D")
			.run(context -> assertThat(context)
				.hasNotFailed()
				.getBean(StorageProperties.class)
				.satisfies(properties -> assertThat(properties.defaultTimeToLive()).isEqualTo(ofDays(7))));
	}

	@EnableConfigurationProperties(StorageProperties.class)
	static class StoragePropertiesConfiguration {
	}
}
