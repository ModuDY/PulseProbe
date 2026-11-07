package com.monitordata.fusion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 融合服务入口：模块化单体，一期不拆分自研微服务。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FusionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FusionApplication.class, args);
    }
}
