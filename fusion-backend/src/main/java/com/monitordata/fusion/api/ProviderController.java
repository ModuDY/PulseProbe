package com.monitordata.fusion.api;

import com.monitordata.fusion.platform.RequestIdFilter;
import com.monitordata.fusion.providers.Provider;
import com.monitordata.fusion.providers.ProviderAdapter;
import com.monitordata.fusion.providers.ProviderRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/providers：查看来源状态、版本、能力与数据新鲜度。
 */
@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderRegistry registry;

    public ProviderController(ProviderRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ProvidersResponse list() {
        List<Provider> items = registry.all().stream().map(ProviderController::toProvider).toList();
        return new ProvidersResponse(RequestIdFilter.currentRequestId(), items);
    }

    private static Provider toProvider(ProviderAdapter adapter) {
        return new Provider(
                adapter.providerId(),
                displayName(adapter),
                adapter.type(),
                null,
                adapter.health(),
                adapter.capabilities());
    }

    private static String displayName(ProviderAdapter adapter) {
        return switch (adapter.type()) {
            case HERTZBEAT -> "HertzBeat（合成数据）";
            case SKYWALKING -> "SkyWalking（合成数据）";
        };
    }

    public record ProvidersResponse(String requestId, List<Provider> items) {
    }
}
