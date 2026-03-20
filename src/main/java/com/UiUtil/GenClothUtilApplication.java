package com.UiUtil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class GenClothUtilApplication {
    public static void main(String[] args) {
        // 个性化启动日志
        printStartupBanner();

        SpringApplication.run(GenClothUtilApplication.class, args);
    }

    private static void printStartupBanner() {
        String banner = "\n" +
                "╔═══════════════════════════════════════════════════════════════╗\n" +
                "║                                                               ║\n" +
                "║   ██████╗ ███████╗███╗   ██╗ ██████╗ ███████╗    ██████╗ ██╗   ██╗██████╗ ███████╗\n" +
                "║  ██╔════╝ ██╔════╝████╗  ██║██╔════╝ ██╔════╝   ██╔════╝ ██║   ██║██╔══██╗██╔════╝\n" +
                "║  ██║  ███╗█████╗  ██╔██╗ ██║██║  ███╗█████╗     ██║  ███╗██║   ██║██████╔╝█████╗  \n" +
                "║  ██║   ██║██╔══╝  ██║╚██╗██║██║   ██║██╔══╝     ██║   ██║██║   ██║██╔══██╗██╔══╝  \n" +
                "║  ╚██████╔╝███████╗██║ ╚████║╚██████╔╝███████╗   ╚██████╔╝╚██████╔╝██║  ██║███████╗\n" +
                "║   ╚═════╝ ╚══════╝╚═╝  ╚═══╝ ╚═════╝ ╚══════╝    ╚═════╝  ╚═════╝ ╚═╝  ╚═╝╚══════╝\n" +
                "║                                                               ║\n" +
                "║  🔥 服装生成工具服务启动中...                                  ║\n" +
                "║  📦 模块: GenClothUtilApplication                              ║\n" +
                "║  🚀 环境: " + System.getProperty("spring.profiles.active", "default") + "                                               ║\n" +
                "║  🌐 访问: http://localhost:8080                            ║\n" +
                "║                                                               ║\n" +
                "╚═══════════════════════════════════════════════════════════════╝\n";

        System.out.println(banner);
    }
}
