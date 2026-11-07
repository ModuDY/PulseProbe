package com.monitordata.fusion.platform;

import java.util.List;

/**
 * 与 fusion-openapi-0.1.json 的 Error schema 对应。
 */
public record ErrorResponse(String requestId, String code, String message, List<SourceState> sources) {
}
