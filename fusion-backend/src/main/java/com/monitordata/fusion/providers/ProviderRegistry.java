package com.monitordata.fusion.providers;

import com.monitordata.fusion.platform.SourceState;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 适配器注册表。按指标归属路由，避免“同名指标被两个来源重复计算”。
 */
@Service
public class ProviderRegistry {

    private final Map<String, ProviderAdapter> adapters = new LinkedHashMap<>();

    public ProviderRegistry(List<ProviderAdapter> discovered) {
        for (ProviderAdapter adapter : discovered) {
            adapters.put(adapter.providerId(), adapter);
        }
    }

    public Collection<ProviderAdapter> all() {
        return adapters.values();
    }

    public Optional<ProviderAdapter> byId(String providerId) {
        return Optional.ofNullable(adapters.get(providerId));
    }

    /**
     * 返回声明拥有该指标的第一个适配器。未声明归属的指标返回空，由调用方标记 unsupported。
     */
    public Optional<Map.Entry<String, ProviderAdapter>> ownerOf(String metricId) {
        for (Map.Entry<String, ProviderAdapter> entry : adapters.entrySet()) {
            Set<String> owned = entry.getValue().ownedMetricIds();
            if (owned.contains(metricId)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public List<SourceState> healthOfAll() {
        return adapters.values().stream().map(ProviderAdapter::health).toList();
    }

    public ProviderAdapter require(String providerId) {
        return byId(providerId).orElseThrow(() -> new com.monitordata.fusion.platform.ApiException(
                com.monitordata.fusion.platform.ApiErrorCode.NOT_FOUND, "来源不存在"));
    }
}
