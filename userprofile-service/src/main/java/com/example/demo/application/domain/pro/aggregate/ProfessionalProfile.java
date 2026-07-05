package com.example.demo.application.domain.pro.aggregate;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.application.domain.pro.event.ProfessionalProfileUpdatedEvent;
import com.example.demo.application.domain.shared.event.DomainEvent;

public class ProfessionalProfile {
    private final String id;
    private String jobTitle;
    private String departmentId;
    private String employeeId;
    private ZoneId timeZone; // 負責處理該員工的跨國排程或會議時區
    private Long version;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public ProfessionalProfile(String id, String jobTitle, String departmentId, String employeeId, ZoneId timeZone, Long version) {
        this.id = id;
        this.jobTitle = jobTitle;
        this.departmentId = departmentId;
        this.employeeId = employeeId;
        this.timeZone = timeZone;
        this.version = version;
    }

    public static ProfessionalProfile createDefault(String userId) {
        return new ProfessionalProfile(userId, null, null, null, ZoneId.of("UTC"), 0L);
    }

    /**
     * <b>【業務變更】調度或晉升 (通常由外部 HR 系統或管理員觸發)</b>
     */
    public void updateJobAssignment(String newJobTitle, String newDepartmentId, String tenantId, String operator) {
        this.jobTitle = newJobTitle;
        this.departmentId = newDepartmentId;
        this.version++;
        this.registerUpdateEvent(tenantId, operator);
    }

    /**
     * <b>【業務變更】員工自行更新所在時區</b>
     */
    public void updateTimeZone(String zoneIdStr, String tenantId, String operator) {
        this.timeZone = ZoneId.of(zoneIdStr);
        this.version++;
        this.registerUpdateEvent(tenantId, operator);
    }

    private void registerUpdateEvent(String tenantId, String operator) {
        this.domainEvents.add(new ProfessionalProfileUpdatedEvent(
                tenantId, this.id, this.jobTitle, this.departmentId, 
                this.employeeId, this.timeZone.getId(), operator, this.version
        ));
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }

    // Getters...
    public String getId() { return id; }
    public String getJobTitle() { return jobTitle; }
    public String getDepartmentId() { return departmentId; }
    public String getEmployeeId() { return employeeId; }
    public ZoneId getTimeZone() { return timeZone; }
    public Long getVersion() { return version; }
}