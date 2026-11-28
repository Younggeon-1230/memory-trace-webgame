package com.metamong.memorytrace.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.metamong.memorytrace.game.dto.GameStateResponse;
import com.metamong.memorytrace.game.dto.SessionStartResult;
import com.metamong.memorytrace.game.service.SessionService;
import com.metamong.memorytrace.game.service.SessionStartFacade;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** 동시 시작의 분기 (docs/backend_design.md 4.6, D25). DB 없이 실행한다. */
class SessionStartFacadeTest {

    private final SessionService service = mock(SessionService.class);
    private final SessionStartFacade facade = new SessionStartFacade(service);
    private final GameStateResponse state = mock(GameStateResponse.class);

    @Test
    void passesNormalResultThrough() {
        SessionStartResult created = new SessionStartResult(state, true);
        when(service.startOrResume(1L, 3L)).thenReturn(created);

        assertThat(facade.start(1L, 3L)).isSameAs(created);
        verify(service, never()).findInProgress(any(), any());
    }

    @Test
    void returnsOtherRequestsSessionAfterConflict() {
        when(service.startOrResume(1L, 3L)).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(service.findInProgress(1L, 3L)).thenReturn(Optional.of(state));

        assertThat(facade.start(1L, 3L)).isEqualTo(new SessionStartResult(state, false));
    }

    @Test
    void rethrowsOriginalWhenNoSessionExists() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other");
        when(service.startOrResume(1L, 3L)).thenThrow(failure);
        when(service.findInProgress(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.start(1L, 3L)).isSameAs(failure);
    }

    @Test
    void facadeHasNoTransaction() throws NoSuchMethodException {
        assertThat(AnnotatedElementUtils.hasAnnotation(SessionStartFacade.class, Transactional.class)).isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(
                SessionStartFacade.class.getMethod("start", Long.class, Long.class), Transactional.class)).isFalse();
    }
}
