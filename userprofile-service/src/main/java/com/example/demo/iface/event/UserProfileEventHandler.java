//package com.example.demo.iface.listener;
//
//import org.apache.kafka.clients.consumer.ConsumerRecord;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import com.example.demo.infra.projection.repository.UserProfileViewRepository;
//import com.example.demo.infra.projection.view.UserProfileView;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
///**
// * <h2>[介面層/投影端] 個人檔案非同步視圖投影監聽器</h2>
// * <p>
// * 專責消費 Kafka 上的 Profile 異動事件，並更新至 Read Model 扁平視圖中。
// * </p>
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class UserProfileEventHandler {
//
//	private final UserProfileViewRepository viewRepository;
//	private final ObjectMapper objectMapper;
//
//	@Transactional
//	@KafkaListener(topics = "topic.user.profile", groupId = "auth-service-profile-syncer")
//	public void onUserProfileEvent(ConsumerRecord<String, String> record) {
//
//		String routingKey = record.key();
//		String message = record.value();
//
//		try {
//			log.info("[CQRS-Projection] 接收到個人檔案事件 - RoutingKey: {}", routingKey);
//
//			// 1. 直接解析扁平的 Event JSON
//			JsonNode payload = objectMapper.readTree(message);
//
//			// 2. 判斷事件類型 (只處理 Profile 更新事件)
//			boolean isUpdatedEvent = routingKey != null && routingKey.endsWith("UserProfileUpdatedEvent");
//			if (!isUpdatedEvent) {
//				log.debug("忽略非個人檔案異動事件: {}", routingKey);
//				return;
//			}
//
//			// 3. 提取事件負載 (Payload)
//			String tenantId = payload.path("tenantId").asText();
//			String aggregateId = payload.path("aggregateId").asText();
//			String displayName = payload.path("displayName").asText();
//			String avatarUrl = payload.path("avatarUrl").asText(null);
//			String bio = payload.path("bio").asText(null);
//			String language = payload.path("language").asText();
//			String theme = payload.path("theme").asText();
//			long eventVersion = payload.path("version").asLong(0L); // 取出版本號
//
//			log.debug("[CQRS-Projection] 準備執行視圖同步 - UserID: {}, EventVersion: {}", aggregateId, eventVersion);
//
//			// 4. 尋找現有視圖並執行 Upsert (更新或插入)
//			viewRepository.findByTenantIdAndId(tenantId, aggregateId).ifPresentOrElse(existingView -> {
//				// 🟢 依賴視圖實體內部的「版本防禦牆」判斷是否更新
//				boolean isUpdated = existingView.syncDetails(displayName, avatarUrl, bio, language, theme,
//						eventVersion);
//				if (isUpdated) {
//					viewRepository.save(existingView);
//					log.info("[CQRS-Projection] 個人檔案視圖更新完成: {}", aggregateId);
//				} else {
//					log.debug("[CQRS-Projection] 忽略亂序或重複的舊事件: {}", aggregateId);
//				}
//			}, () -> {
//				// 🟢 若視圖不存在 (例如新建帳號首次觸發)，則直接創建新視圖
//				UserProfileView newView = UserProfileView.createNew(aggregateId, tenantId, displayName, avatarUrl, bio,
//						language, theme, eventVersion);
//				viewRepository.save(newView);
//				log.info("[CQRS-Projection] 個人檔案視圖創建完成: {}", aggregateId);
//			});
//
//		} catch (Exception e) {
//			log.error("[CQRS-Projection] 個人檔案視圖同步發生系統異常，訊息: {}", message, e);
//		}
//	}
//}