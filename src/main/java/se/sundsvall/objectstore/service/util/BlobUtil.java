package se.sundsvall.objectstore.service.util;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.sql.Blob;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Component
public class BlobUtil {

	private static final Logger LOG = LoggerFactory.getLogger(BlobUtil.class);
	private static final String ERROR_CREATE_BLOB = "Could not store the content of the uploaded file";

	private final EntityManager entityManager;

	public BlobUtil(final EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public Blob toBlob(final byte[] content) {
		try {
			return getSession().getLobHelper().createBlob(new ByteArrayInputStream(content), content.length);
		} catch (final Exception e) {
			LOG.warn("Failed to create blob from {} bytes of content", content.length, e);
			throw Problem.valueOf(INTERNAL_SERVER_ERROR, ERROR_CREATE_BLOB);
		}
	}

	Session getSession() {
		return entityManager.unwrap(Session.class);
	}
}
