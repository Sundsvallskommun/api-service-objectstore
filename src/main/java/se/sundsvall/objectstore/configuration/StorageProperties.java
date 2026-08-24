package se.sundsvall.objectstore.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Settings for the object storage.
 *
 * @param defaultTimeToLive the time to live applied to stored objects when the client provides no explicit expiry
 * @param maxObjectSize     the largest object accepted by an upload. Since objects are sent as a raw request body there
 *                          is no framework-enforced limit, so this is the only thing bounding the memory an upload
 *                          consumes.
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(Duration defaultTimeToLive, DataSize maxObjectSize) {
}
