package com.example.demo.application.domain.ext.aggregate;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.demo.application.domain.ext.event.ExternalLinksUpdatedEvent;
import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.shared.vo.SocialLink;
import com.example.demo.application.domain.shared.vo.SocialPlatform;

public class ExternalLinks {
	private final String id;
	// 使用 Map 保證同一個 Platform 只能有一個 URL
	private final Map<SocialPlatform, SocialLink> links;
	private Long version;
	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private static final int MAX_LINKS_ALLOWED = 10;

	public ExternalLinks(String id, Set<SocialLink> initialLinks, Long version) {
		this.id = id;
		this.links = new HashMap<>();
		if (initialLinks != null) {
			initialLinks.forEach(link -> this.links.put(link.platform(), link));
		}
		this.version = version;
	}

	public static ExternalLinks createEmpty(String userId) {
		return new ExternalLinks(userId, Collections.emptySet(), 0L);
	}

	/**
	 * <b>【業務變更】新增或更新外部連結</b>
	 */
	public void addOrUpdateLink(SocialLink newLink, String tenantId, String operator) {
		if (!this.links.containsKey(newLink.platform()) && this.links.size() >= MAX_LINKS_ALLOWED) {
			throw new IllegalStateException("Cannot add more than " + MAX_LINKS_ALLOWED + " external links.");
		}

		this.links.put(newLink.platform(), newLink);
		this.version++;
		this.registerUpdateEvent(tenantId, operator);
	}

	/**
	 * <b>【業務變更】移除指定的社交平台連結</b>
	 */
	public void removeLink(SocialPlatform platform, String tenantId, String operator) {
		if (this.links.remove(platform) != null) {
			this.version++;
			this.registerUpdateEvent(tenantId, operator);
		}
	}

	private void registerUpdateEvent(String tenantId, String operator) {
		// 將 Map 轉換為容易被 JSON 序列化的 Map<String, String> 結構發送
		Map<String, String> snapshot = this.links.values().stream()
				.collect(Collectors.toMap(link -> link.platform().name(), SocialLink::url));

		this.domainEvents.add(new ExternalLinksUpdatedEvent(tenantId, this.id, snapshot, operator, this.version));
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(this.domainEvents);
		this.domainEvents.clear();
		return events;
	}

	// Getters (回傳不可變集合防禦外部竄改)
	public String getId() {
		return id;
	}

	public Set<SocialLink> getLinks() {
		return Set.copyOf(links.values());
	}

	public Long getVersion() {
		return version;
	}
}