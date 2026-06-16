package com.example.demo.application.domain.shared.core;

import com.example.demo.application.domain.shared.event.DomainEvent;
import jakarta.persistence.Transient;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import jakarta.persistence.MappedSuperclass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <h2>聚合根基底類別 (Base Aggregate Root)</h2>
 * <p>
 * <b>【架構定位】</b>：<br>
 * 實作 Layer Supertype 模式，封裝所有聚合根共用的事件發布 (Event Sourcing / Event-Driven) 基礎設施邏輯。<br>
 * 確保子類別能專注於充血業務邏輯，徹底消滅重複的 Boilerplate 程式碼。
 * </p>
 */
@MappedSuperclass
public abstract class BaseAggregateRoot {

    /**
     * 領域事件動態暫存區
     */
    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 供 Spring Data JPA 攔截並廣播事件
     */
    @DomainEvents
    protected List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 廣播完畢後自動清理，防堵重複派發
     */
    @AfterDomainEventPublication
    protected void clearDomainEvents() {
        this.domainEvents.clear();
    }

    /**
     * 供子類別呼叫，將領域事件送入記憶體暫存箱
     * <p>💡 權限設為 protected，嚴格限制只有聚合根內部可以發布事件。</p>
     */
    protected void raise(DomainEvent event) {
        if (event != null) {
            this.domainEvents.add(event);
        }
    }
}