package com.example.demo.application.shared.command.outbound;

/**
 * <h2>發布事件指令 (Publish Event Command)</h2>
 * <p>
 * 封裝向外部訊息總線 (MQ) 發布事件所需的完整參數。 💡 使用 record 保證指令的不可變性
 * (Immutability)，確保在非同步處理時的安全。
 * </p>
 *
 * @param topic      目標主題 (Topic)
 * @param routingKey 路由鍵/訊息鍵 (Message Key)，用於確保同一聚合根的事件循序寫入同一個 Partition
 * @param eventJson  已序列化為 JSON 格式的事件 Payload
 */
public record PublishEventCommand(String topic, String routingKey, String eventJson) {
}