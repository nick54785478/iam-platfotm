package com.example.demo.iface.schedule;


import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.application.port.OutboxEventPublisherPort;
import com.example.demo.infra.outbox.entity.OutboxEventDbEntity;
import com.example.demo.infra.outbox.repository.OutboxRepository;

/**
 * <h2>[基礎設施層 - 排程引擎] Outbox 事件導流與發射器 (Outbox Exporter)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為分散式事務保證（At-least-once Delivery）的傳輸馬達。 負責以極高頻率輪詢資料庫中狀態為 {@code PENDING}
 * 的事件紀錄，並透過解耦的 Port 接口將其發射出去。
 * </p>
 * <p>
 * <b>【技術亮點】</b>：<br>
 * 1. <b>面向介面設計 (Port & Adapter)</b>：目前上游注入的是
 * {@code InMemoryOutboxPublisherAdapter}， 未來切換到 Kafka
 * 時，本引擎<b>不需要改動任何一行程式碼</b>。<br>
 * 2. <b>叢集併發安全 (樂觀鎖)</b>：利用 {@link OutboxEventDbEntity} 的 {@code @Version} 屬性。
 * 當多個 K8s 節點同時撈到同一批任務時，資料庫會硬核阻斷重複 save，完美瓦解併發覆蓋隱患。
 * </p>
 */
@Component
public class OutboxExporter {

	private final OutboxRepository outboxRepository;
	private final OutboxEventPublisherPort outboxEventPublisherPort; // 🚀 面向規格程式設計，解耦本地記憶體與未來 Kafka

	public OutboxExporter(OutboxRepository outboxRepository,
                          OutboxEventPublisherPort outboxEventPublisherPort) {
		this.outboxRepository = outboxRepository;
		this.outboxEventPublisherPort = outboxEventPublisherPort;
	}

	/**
	 * <b>定時輪詢發射任務</b>
	 * <p>
	 * 每 500 毫秒甦醒一次。採取「小步快跑」戰略（每次上限 20 筆），避免長時間霸佔資料庫連接池。
	 * </p>
	 */
	@Scheduled(fixedDelay = 500)
	public void exportPendingEvents() {
		// 1. 依序撈出一小批最早產生的 PENDING 變更事件
		List<OutboxEventDbEntity> pendingEvents = outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING");

		for (OutboxEventDbEntity outbox : pendingEvents) {
			try {
				// 2. 呼叫發射 Port。目前過渡期會直接把 JSON 反序列化後拋給本地 Spring 容器處理
				outboxEventPublisherPort.publish(outbox);

				// 3. 發射安全落地後，充血模型變更狀態為 PROCESSED (結案)
				outbox.markAsProcessed();
				outboxRepository.save(outbox); // 🚀 樂觀鎖防線：若別的 Pod 捷足先登了，這行會秒拋異常並自動 Rollback

			} catch (ObjectOptimisticLockingFailureException e) {
				// 溫和防禦：併發衝突，說明別的節點已經搶先發射並結案了，這裡直接優雅跳過
				System.out.println("Event " + outbox.getId() + " already processed by another worker.");
			} catch (Exception e) {
				// 🚀 容錯閉環：真正的發射失敗（如解析崩潰或未來微服務斷線），打上 FAILED 標籤並留存異常 Stack，防止卡死排程
				outbox.markAsFailed(e.getMessage());
				outboxRepository.save(outbox);
			}
		}
	}
}