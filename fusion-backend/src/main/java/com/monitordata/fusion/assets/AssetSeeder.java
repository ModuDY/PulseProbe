package com.monitordata.fusion.assets;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Map;

/**
 * 试点场景种子数据：一个站点、两台主机、一个应用服务及其实例、一个 MariaDB 实例。
 * 这些 ID 在本地固定，便于前端与契约测试引用；不代表已接入真实环境。
 */
@Configuration
public class AssetSeeder {

    public static final String SITE_ID = "11111111-1111-4111-8111-111111111111";
    public static final String ENVIRONMENT = "pilot";

    public static final String APP_HOST = "22222222-2222-4222-8222-222222222222";
    public static final String DB_HOST = "33333333-3333-4333-8333-333333333333";
    public static final String APP_SERVICE = "44444444-4444-4444-8444-444444444444";
    public static final String APP_INSTANCE = "55555555-5555-4555-8555-555555555555";
    public static final String APP_CONTAINER = "66666666-6666-4666-8666-666666666666";
    public static final String MARIADB = "77777777-7777-4777-8777-777777777777";

    @Bean
    ApplicationRunner seedAssets(AssetRegistry registry) {
        return args -> {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");

            registry.put(new Asset(APP_HOST, SITE_ID, ENVIRONMENT, AssetType.HOST, null,
                    "app-host-01", Map.of("os", "linux", "role", "application")));
            registry.put(new Asset(DB_HOST, SITE_ID, ENVIRONMENT, AssetType.HOST, null,
                    "db-host-01", Map.of("os", "linux", "role", "database")));
            registry.put(new Asset(APP_SERVICE, SITE_ID, ENVIRONMENT, AssetType.SERVICE, null,
                    "zing-icu-system", Map.of("runtime", "spring-boot-2.7", "jvm", "1.8.0_202")));
            registry.put(new Asset(APP_INSTANCE, SITE_ID, ENVIRONMENT, AssetType.SERVICE_INSTANCE, APP_SERVICE,
                    "zing-icu-server", Map.of("replica", "1")));
            registry.put(new Asset(APP_CONTAINER, SITE_ID, ENVIRONMENT, AssetType.CONTAINER, APP_HOST,
                    "zing-icu-server-container", Map.of("image", "zing-icu-system-start")));
            registry.put(new Asset(MARIADB, SITE_ID, ENVIRONMENT, AssetType.DATABASE, DB_HOST,
                    "maria-business", Map.of("engine", "mariadb", "version", "10.5.6")));

            registry.relate(new AssetRelation(APP_CONTAINER, APP_HOST, RelationType.RUNS_ON, from, null));
            registry.relate(new AssetRelation(APP_INSTANCE, APP_CONTAINER, RelationType.CONTAINS, from, null));
            registry.relate(new AssetRelation(MARIADB, DB_HOST, RelationType.RUNS_ON, from, null));
            registry.relate(new AssetRelation(APP_INSTANCE, MARIADB, RelationType.CONNECTS_TO, from, null));
        };
    }
}
