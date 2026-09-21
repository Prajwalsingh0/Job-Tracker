package com.jobhunt.controller;

import com.jobhunt.dto.AiStatusDto;
import com.jobhunt.dto.GenerateRequest;
import com.jobhunt.dto.GenerateResponse;
import com.jobhunt.dto.MatchRequest;
import com.jobhunt.dto.MatchResponse;
import com.jobhunt.security.UserPrincipal;
import com.jobhunt.service.AiCareerService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiCareerService aiCareerService;

    public AiController(AiCareerService aiCareerService) {
        this.aiCareerService = aiCareerService;
    }

    /** Lets the UI show a setup hint instead of a broken feature. */
    @GetMapping("/status")
    public AiStatusDto status() {
        return aiCareerService.status();
    }

    /**
     * Deterministic resume-to-job matching. Available without any AI provider.
     */
    @PostMapping("/match")
    public MatchResponse match(@AuthenticationPrincipal UserPrincipal principal,
                               @Valid @RequestBody MatchRequest request) {
        return aiCareerService.match(principal.getId(), request);
    }

    /**
     * Provider-backed generation. Returns 503 with a setup instruction when no provider is
     * configured — never fabricated content.
     */
    @PostMapping("/generate")
    public GenerateResponse generate(@AuthenticationPrincipal UserPrincipal principal,
                                     @Valid @RequestBody GenerateRequest request) {
        return aiCareerService.generate(principal.getId(), request);
    }
}
