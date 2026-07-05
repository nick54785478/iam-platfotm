package com.example.demo.application.domain.comunication.aggregate;

import java.util.ArrayList;
import java.util.List;

import com.example.demo.application.domain.comunication.event.CommunicationPreferencesUpdatedEvent;
import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.shared.vo.NotificationSetting;

public class CommunicationPreferences {
	private final String id;
	private NotificationSetting marketing;
	private NotificationSetting systemUpdates;
	private Long version;
	private final List<DomainEvent> domainEvents = new ArrayList<>();

	public CommunicationPreferences(String id, NotificationSetting marketing, NotificationSetting systemUpdates,
			Long version) {
		this.id = id;
		this.marketing = marketing;
		this.systemUpdates = systemUpdates;
		this.version = version;
	}

	public static CommunicationPreferences createDefault(String userId) {
		// 預設關閉行銷通知 (符合多數現代隱私法規)，開啟系統更新通知
		return new CommunicationPreferences(userId, NotificationSetting.allDisabled(), NotificationSetting.allEnabled(),
				0L);
	}

	/**
	 * <b>【業務變更】更新行銷通知偏好</b>
	 */
	public void updateMarketingPreferences(boolean email, boolean inApp, boolean sms, String tenantId,
			String operator) {
		this.marketing = new NotificationSetting(email, inApp, sms);
		this.version++;
		this.registerUpdateEvent(tenantId, operator);
	}

	/**
	 * <b>【業務變更】更新系統更新通知偏好</b>
	 */
	public void updateSystemUpdatePreferences(boolean email, boolean inApp, boolean sms, String tenantId,
			String operator) {
		this.systemUpdates = new NotificationSetting(email, inApp, sms);
		this.version++;
		this.registerUpdateEvent(tenantId, operator);
	}

	private void registerUpdateEvent(String tenantId, String operator) {
		this.domainEvents.add(new CommunicationPreferencesUpdatedEvent(tenantId, this.id, this.marketing.email(),
				this.marketing.inApp(), this.marketing.sms(), this.systemUpdates.email(), this.systemUpdates.inApp(),
				this.systemUpdates.sms(), operator, this.version));
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(this.domainEvents);
		this.domainEvents.clear();
		return events;
	}

	// Getters...
	public String getId() {
		return id;
	}

	public NotificationSetting getMarketing() {
		return marketing;
	}

	public NotificationSetting getSystemUpdates() {
		return systemUpdates;
	}

	public Long getVersion() {
		return version;
	}
}