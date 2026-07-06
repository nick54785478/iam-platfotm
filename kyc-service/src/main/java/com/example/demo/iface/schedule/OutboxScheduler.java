package com.example.demo.iface.schedule;

import com.example.demo.application.port.DistributedLockManagerPort;
import com.example.demo.application.port.MessagePublisherPort;
import com.example.demo.application.shared.command.outbound.PublishEventCommand;
import com.example.demo.infra.outbox.entity.OutboxEventEntity;
import com.example.demo.infra.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

/**
 * <h2>[Interface Layer] 發件箱非同步發射排程器 (Cluster Outbox Exporter) - 完全體</h2>
 * <p>
 * 透過 Spring @Scheduled 實作高頻非同步輪詢。全面依賴 Ports 介面實現基礎設施級解耦：<br>
 * 1. <b>DistributedLockManagerPort</b>：消除手動防死鎖與釋放鎖的樣板代碼，交由高階範本生命週期維護。<br>
 * 2. <b>MessagePublisherPort</b>：將排程器與特定的 Kafka 技術細節徹底隔離，捍衛六角架構內圈的純潔性。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

	private final OutboxRepository outboxRepository;
	private final DistributedLockManagerPort lockManager; // 依賴抽象鎖埠，解除 H2/Postgres 技術綁定
	private final MessagePublisherPort messagePublisher; // 依賴抽象發布埠，解除特定 MQ 技術綁定
	private final PlatformTransactionManager transactionManager;

	private static final String OUTBOX_LOCK_KEY = "job:outbox-export-lock";
	private static final Duration LOCK_DURATION = Duration.ofSeconds(30);
	private PublishEventCommand command;

	/**
	 * <b>高頻發射任務：每 2 秒發動一次突襲</b>
	 */
	@Scheduled(fixedDelay = 2000)
	public void exportPendingEvents() {
		// 使用高階範本 API：自動處理競爭、任務執行，並於內建的 finally 區塊中安全解鎖，徹底防禦死鎖
		lockManager.executeWithLock(OUTBOX_LOCK_KEY, LOCK_DURATION, () -> {
			log.debug("[Outbox-Job] 成功霸占關係型分散式鎖，開始清點待發射事件...");
			try {
				// 獨立事務邊界控制，採每次 20 筆的小批次 FIFO 模式，防止撐爆記憶體
				TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
				txTemplate.executeWithoutResult(status -> processBatch());
			} catch (Exception e) {
				log.error("[Outbox-Job] 本次批次發射事件遭遇異常，等待下個週期重新自癒。", e);
			}
		});
	}

	/**
	 * 處理單一小批次的事務區塊
	 */
	private void processBatch() {
		// 1. 撈出最老的 20 筆待發射事件
		List<OutboxEventEntity> pendingEvents = outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING");

		if (pendingEvents.isEmpty()) {
			return;
		}

		log.info("[Outbox-Job] 本次捕獲 {} 筆 PENDING 事件，開始組裝並交付輸出埠...", pendingEvents.size());

		for (OutboxEventEntity event : pendingEvents) {
			try {
				// 2. 映射路由 Topic
				String targetTopic = resolveTopic(event.getAggregateType());

				// 3. 規格對齊：將物理儲存實體封裝為不可變的領域發布指令 (PublishEventCommand)
				PublishEventCommand command = new PublishEventCommand(targetTopic, event.getId().toString(),
						event.getPayload());

				System.out.println("command: "+ command);
				// 4. 透過解耦的 Port 發動非同步傳輸，底層由 Kafka 適配器接管 ACK 回執
//				messagePublisher.send(command);

				// 5. 變更狀態為已處理 (Mark Processed)
				event.markAsProcessed();
				outboxRepository.save(event);

			} catch (Exception e) {
				log.error("[Outbox-Job] 封裝或交付領域事件至發布埠時遭遇阻斷。EventId: {}", event.getId(), e);
				// 降級防禦：更新狀態為 FAILED，留存錯誤日誌，防止該死信（Dead Letter）無限期卡死輪詢佇列
				event.markAsFailed(e.getMessage());
				outboxRepository.save(event);
			}
		}
	}

	/**
     * 依據領域型態，動態決定目標主題
     */
    private String resolveTopic(String aggregateType) {
        return switch (aggregateType) {
            case "TENANT" -> "topic.platform.tenant";
            case "USER", "ROLE" -> "topic.auth.permission";
            case "UserIdentity" -> "topic.kyc.status";
            default -> "topic.general.outbox";
        };
    }
}