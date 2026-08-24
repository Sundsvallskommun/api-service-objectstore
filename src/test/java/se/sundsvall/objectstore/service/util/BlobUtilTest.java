package se.sundsvall.objectstore.service.util;

import jakarta.persistence.EntityManager;
import java.sql.Blob;
import org.hibernate.LobHelper;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.dept44.problem.Problem;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@ExtendWith(MockitoExtension.class)
class BlobUtilTest {

	@Mock
	private EntityManager entityManagerMock;

	@InjectMocks
	private BlobUtil blobUtil;

	@Test
	void getSession() {
		// Arrange
		final var session = Mockito.mock(Session.class);
		when(entityManagerMock.unwrap(Session.class)).thenReturn(session);

		// Act
		final var result = blobUtil.getSession();

		// Assert
		assertThat(result).isSameAs(session);
		verify(entityManagerMock).unwrap(Session.class);
	}

	@Test
	void toBlob() {
		// Arrange
		final var content = "content".getBytes(UTF_8);
		final var session = Mockito.mock(Session.class);
		final var lobHelper = Mockito.mock(LobHelper.class);
		final var blob = Mockito.mock(Blob.class);

		when(entityManagerMock.unwrap(Session.class)).thenReturn(session);
		when(session.getLobHelper()).thenReturn(lobHelper);
		when(lobHelper.createBlob(any(), eq(7L))).thenReturn(blob);

		// Act
		final var result = blobUtil.toBlob(content);

		// Assert
		assertThat(result).isSameAs(blob);
		verify(session).getLobHelper();
		verify(lobHelper).createBlob(any(), eq(7L));
	}

	@Test
	void toBlobWhenBlobCannotBeCreated() {
		// Arrange
		final var session = Mockito.mock(Session.class);

		when(entityManagerMock.unwrap(Session.class)).thenReturn(session);
		when(session.getLobHelper()).thenThrow(new IllegalStateException("boom"));

		// Act & Assert
		assertThatThrownBy(() -> blobUtil.toBlob("content".getBytes(UTF_8)))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("Could not store the content of the uploaded file")
			.extracting("status").isEqualTo(INTERNAL_SERVER_ERROR);
	}
}
