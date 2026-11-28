package com.metamong.memorytrace.cases.controller;

import com.metamong.memorytrace.auth.service.UserPrincipal;
import com.metamong.memorytrace.cases.dto.CaseSummaryResponse;
import com.metamong.memorytrace.cases.service.CaseQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseQueryService caseQueryService;

    /** 사용자 ID는 로그인 정보에서만 가져온다. */
    @GetMapping
    public List<CaseSummaryResponse> cases(@AuthenticationPrincipal UserPrincipal principal) {
        return caseQueryService.findCases(principal.getUserId());
    }
}
