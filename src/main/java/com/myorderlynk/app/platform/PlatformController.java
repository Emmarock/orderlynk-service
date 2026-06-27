package com.myorderlynk.app.platform;

import com.myorderlynk.app.platform.PlatformDtos.PlatformStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated platform-wide metrics for the OrderLynk home page. */
@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final PlatformStatsService statsService;

    public PlatformController(PlatformStatsService statsService) {
        this.statsService = statsService;
    }

    /** Headline numbers for the public home page stats strip. */
    @GetMapping("/stats")
    public PlatformStats stats() {
        return statsService.stats();
    }
}