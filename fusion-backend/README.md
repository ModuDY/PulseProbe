# fusion-backend

第三方性能监控与诊断平台中的融合服务，模块化单体。设计与接口契约见仓库根目录
`monitoring-engineering-design-v1.md` 与 `fusion-openapi-0.1.json`。

## 当前状态

已完成的是**契约骨架 + 合成数据适配器**，用于前后端并行开发与契约回归。尚未接入真实
HertzBeat 与 SkyWalking，`/api/v1/providers` 会把相关能力标记为 `unverified`，页面数据
必须按合成数据展示。

| 接口 | 状态 |
|---|---|
| `GET /api/v1/providers` | 可用（数据来自适配器自述） |
| `GET /api/v1/assets` | 可用（内存种子数据） |
| `GET /api/v1/assets/{id}/relations` | 可用（带有效时间） |
| `GET /api/v1/overview` | 可用（部分失败可见） |
| `POST /api/v1/metrics/query` | 可用（有界查询，超限报错） |
| traces / incidents / reports / ingest | 未实现 |

## 运行

```bash
mvn -o test          # 契约测试
mvn -o spring-boot:run
```

默认端口 8080。示例请求见 `http/` 目录。

## 目录约定

```
src/main/java/com/monitordata/fusion/
  api/            REST 控制器，只做参数解析与 DTO 装配
  assets/         资产、关系与内存登记表
  metrics/        指标目录、口径、有界查询与融合路由
  providers/      适配器 SPI 与注册表
  adapters/mock/  合成数据实现，接入真实上游时按 provider 替换
  platform/       错误码、统一异常、requestId、时间解析
```

## 不可退让的实现约束

1. 缺失数据必须用 `quality=no_data/unknown/unsupported` 表达，禁止填 0。
2. 采样比例未知时 `sampleScope.mode=unknown`，不编造 ratio。
3. 点数或组合数超限返回 400 机器码，不做静默降采样或截断；如发生截断必须置
   `truncated=true`。
4. 资产与来源通过 `provider_asset_mapping` 关联，不用名称拼 ID。
5. 凭证只保存引用，不进日志、响应与报告。
