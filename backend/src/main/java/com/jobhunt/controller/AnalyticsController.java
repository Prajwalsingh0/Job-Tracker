package com.jobhunt.controller;

import com.jobhunt.dto.AnalyticsDto;
import com.jobhunt.security.UserPrincipal;
import com.jobhunt.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Analytics for a date range. Both bounds are optional; the default is the last six
     * months up to today.
     */
    @GetMapping
    public AnalyticsDto analytics(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.analyse(principal.getId(), from, to);
    }
}
