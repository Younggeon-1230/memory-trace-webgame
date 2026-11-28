package com.metamong.memorytrace.game.controller;

import com.metamong.memorytrace.auth.service.UserPrincipal;
import com.metamong.memorytrace.game.dto.GameStateResponse;
import com.metamong.memorytrace.game.dto.SessionStartResult;
import com.metamong.memorytrace.game.service.SessionService;
import com.metamong.memorytrace.game.service.SessionStartFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionStartFacade sessionStartFacade;
    private final SessionService sessionService;

    /** 새로 만들면 201, 진행 중 세션을 이어 하면 200. */
    @PostMapping("/api/cases/{caseId}/sessions")
    public ResponseEntity<GameStateResponse> start(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long caseId) {
        SessionStartResult result = sessionStartFacade.start(principal.getUserId(), caseId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.state());
    }

    @GetMapping("/api/sessions/{sessionId}")
    public GameStateResponse state(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable Long sessionId) {
        return sessionService.getState(principal.getUserId(), sessionId);
    }
}
