package security.config;


import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * <h2>Shared Kernel 自動裝配核心</h2>
 * <p>當其他微服務引入此 JAR 包時，此設定會引導 Spring Boot 掃描並註冊安全防護元件。</p>
 */
@AutoConfiguration
// 替換成你 shared-kernel 實際的根目錄 package
@ComponentScan("security")
public class SharedSecurityAutoConfiguration {
    // 這裡留空即可，@ComponentScan 會自動幫你把 PermissionGuardInterceptor 抓出來變成 Bean
}