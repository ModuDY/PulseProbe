package com.monitordata.fusion.providers;

import com.monitordata.fusion.platform.SourceState;

import java.util.Map;

/**
 * 上游来源。不包含凭证或含密钥的 URL。
 */
public record Provider(
        String id,
        String name,
        ProviderType type,
        String version,
        SourceState state,
        Map<String, CapabilityState> capabilities) {
}
