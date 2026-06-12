package com.myorderlynk.app.batch;

import com.myorderlynk.app.batch.BatchDtos.BatchResponse;
import com.myorderlynk.app.batch.BatchDtos.BatchSummary;
import com.myorderlynk.app.batch.BatchDtos.StatusUpdateRequest;
import com.myorderlynk.app.security.access.IsAdmin;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    public List<BatchSummary> all() {
        return batchService.adminListAll();
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
