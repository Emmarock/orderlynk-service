package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.BatchResponse;
import com.myorderlynk.app.batch.BatchDtos.BatchSummary;
import com.myorderlynk.app.batch.BatchDtos.StatusUpdateRequest;
import com.myorderlynk.app.common.PageResponse;
import com.myorderlynk.app.security.access.IsAdmin;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin oversight of all batches (spec §9 Admin Dashboard, §15, §16). */
@RestController
@RequestMapping("/api/admin/batches")
@IsAdmin
public class AdminBatchController {

    private final BatchService batchService;

    public AdminBatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public PageResponse<BatchSummary> all(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return batchService.adminListAll(page, size);
    }

    @GetMapping("/{id}")
    public BatchSummary get(@PathVariable UUID id) {
        return batchService.adminGet(id);
    }

    /** Manually correct a batch's status across the platform. */
    @PatchMapping("/{id}/status")
    public BatchResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest req) {
        return batchService.adminUpdateStatus(id, req.status());
    }
}
