package com.monitordata.fusion.api;

import com.monitordata.fusion.metrics.MetricQuery;
import com.monitordata.fusion.metrics.MetricQueryResponse;
import com.monitordata.fusion.metrics.MetricQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/metrics/query：有界的统一指标查询。
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {

    private final MetricQueryService service;

    public MetricController(MetricQueryService service) {
        this.service = service;
    }

    @PostMapping("/query")
    public MetricQueryResponse query(@Valid @RequestBody MetricQuery query) {
        return service.query(query);
    }
}
