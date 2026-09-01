package se.sundsvall.objectstore.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the object storage. Validated, so that a maximum object size left unconfigured is a service that refuses
 * to start rather than one that starts and answers the first upload with an internal error — it is the only bound on
 * the memory an upload consumes, and there is no sensible value to fall back on.
 *
 * @param defaultTimeToLive the time to live applied to stored objects when the client provides no explicit expiry.
 *                          Optional — objects are stored without an expiry when it is left unset.
 * @param maxObjectSize     the largest object accepted by an upload. Since objects are sent as a raw request body there
 *                          is no framework-enforced limit, so this is the only thing bounding the memory an upload
 *                          consumes.
 */
@Validated
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(Duration defaultTimeToLive, @NotNull DataSize maxObjectSize) {
}
