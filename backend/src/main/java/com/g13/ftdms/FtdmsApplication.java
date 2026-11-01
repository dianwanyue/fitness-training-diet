package com.g13.ftdms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * FTDMS 后端服务启动类。
 *
 * <p>职责：作为 Spring Boot 应用的唯一入口，完成组件扫描、配置属性绑定与事务管理开关。
 * 不做任何业务逻辑，业务实现分别落在 controller / service / repository 三层。</p>
 *
 * <p>启动方式：
 * <ul>
 *   <li>开发：{@code mvn spring-boot:run}（默认连接 MySQL）</li>
 *   <li>演示：{@code mvn spring-boot:run -Dspring-boot.run.profiles=h2}（内置 H2，无需装库）</li>
 *   <li>部署：{@code java -jar ftdms-backend-1.0.0.jar}</li>
 * </ul></p>
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@ConfigurationPropertiesScan
public class FtdmsApplication {

    /**
     * 进程入口。
     *
     * @param args 命令行参数，可覆盖任意配置项（如 --server.port=9090）
     */
    public static void main(String[] args) {
        SpringApplication.run(FtdmsApplication.class, args);
    }
}
