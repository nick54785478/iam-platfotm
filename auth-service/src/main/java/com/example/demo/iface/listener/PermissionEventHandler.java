package com.example.demo.iface.listener;

import com.example.demo.infra.persistence.repository.PermissionDictRepository;
import com.example.demo.infra.projection.view.PermissionDictView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>[介面層/投影端] 權限字典非同步視圖投影監聽器</h2>
 * <p>
 * 💡 <b>修復亮點：</b><br>
 * 拔除了原先為 Debezium 設計的 `after` 節點判斷，直接適配由 DeptService
 * 透過 Java KafkaTemplate 送出的扁平化 DomainEvent JSON。<br>
 * 引入 {@code ConsumerRecord} 以獲取 Header 與 RoutingKey。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionEventHandler {

    private final PermissionDictRepository jpaRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "topic.auth.permission", groupId = "auth-service-permission-syncer")
    public void onPermissionEvent(ConsumerRecord<String, String> record) {

        String routingKey = record.key();   // 例如: SYSTEM.PermissionDefinition.PermissionDefinitionCreatedEvent
        String message = record.value();    // 扁平的 JSON 內容

        try {
            log.info("[CQRS-Projection] 接收到遠端事件 - RoutingKey: {}", routingKey);

            // 1. 直接解析扁平的 Event JSON
            JsonNode payload = objectMapper.readTree(message);

            // 2. 判斷事件類型 (依據你截圖上的 RoutingKey 結尾來判斷)
            boolean isCreatedEvent = routingKey != null && routingKey.endsWith("CreatedEvent");
            boolean isUpdatedEvent = routingKey != null && routingKey.endsWith("UpdatedEvent");

            if (!isCreatedEvent && !isUpdatedEvent) {
                log.debug("忽略非權限異動事件: {}", routingKey);
                return;
            }

            // 3. 直接從第一層 payload 提取業務欄位 (需確認 DeptService 送出的事件有這些欄位名稱)
            // 💡 使用 path().asText() 比 get().asText() 更安全，防範 NullPointerException
            String tenantId = payload.path("tenantId").asText();
            String code = payload.path("code").asText();
            String permId = payload.path("permissionId").asText(); // 依據截圖，你確實有傳 permissionId
            String name = payload.path("name").asText();
            String description = payload.path("description").asText();
            String module = payload.path("module").asText();

            long eventVersion = payload.path("version").asLong(0L); // 🚀 萃取版本號

            log.info("[CQRS-Projection] 準備執行充血模型投影變更 - Code: {}, EventVersion: {}", code, eventVersion);

            jpaRepo.findByTenantIdAndCode(tenantId, code)
                    .ifPresentOrElse(
                            existing -> {
                                // 🟢 實體內部自行判斷版本，若回傳 false 代表這是舊事件，直接吃掉不存 DB
                                boolean isUpdated = existing.syncDetails(name, description, module, eventVersion);
                                if (isUpdated) {
                                    jpaRepo.save(existing);
                                    log.info("[CQRS-Projection] 充血投影更新完成: {}", code);
                                } else {
                                    log.debug("[CQRS-Projection] 忽略亂序或重複的舊事件: {}", code);
                                }
                            },
                            () -> {
                                // 🟢 新增時，一併把初始版本號塞進去
                                PermissionDictView newPo = PermissionDictView.createNew(
                                        permId, tenantId, code, name, description, module, eventVersion
                                );
                                jpaRepo.save(newPo);
                                log.info("[CQRS-Projection] 充血投影創建完成: {}", code);
                            }
                    );

        } catch (Exception e) {
            // 這裡會捕捉 JsonMappingException 以及其他系統異常
            log.error("[CQRS-Projection] 權限字典同步投影發生系統異常，訊息: {}", message, e);
        }
    }
}