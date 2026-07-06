package com.example.demo.application.domain.user.aggregate;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.user.aggregate.vo.Address;
import com.example.demo.application.domain.user.aggregate.vo.KycStatus;
import com.example.demo.application.domain.user.aggregate.vo.NationalId;
import com.example.demo.application.domain.user.aggregate.vo.RealName;
import com.example.demo.application.domain.user.aggregate.vo.VerificationAudit;
import com.example.demo.application.domain.user.event.KycStatusChangedEvent;

/**
 * <h2>[領域層 - 聚合根] 使用者真實身分與 KYC 模型</h2>
 * <p>負責高敏感資料的安全流轉與實名認證狀態機。嚴格綁定租戶邊界。</p>
 */
public class UserIdentity {

    // --- 實體邊界 ---
    private final String id;       // 與 UserId 共享 (1:1)
    private final String tenantId; // 🚀 租戶邊界 (絕對不可變)

    // --- 高機密 PII ---
    private NationalId nationalId;
    private RealName realName;
    private LocalDate dateOfBirth;
    private Address residentialAddress; // 🚀 新增居住地證明

    // --- 狀態與防禦 ---
    private KycStatus status;
    private VerificationAudit lastAudit;

    private Long version;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * <b>【重建用建構式】供 Repository 從資料庫載入使用</b>
     */
    public UserIdentity(String id, String tenantId, NationalId nationalId, RealName realName,
                        LocalDate dateOfBirth, Address residentialAddress, KycStatus status,
                        VerificationAudit lastAudit, Long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.nationalId = nationalId;
        this.realName = realName;
        this.dateOfBirth = dateOfBirth;
        this.residentialAddress = residentialAddress;
        this.status = status;
        this.lastAudit = lastAudit;
        this.version = version;
    }

    /**
     * <b>【工廠方法】使用者註冊時，初始化空白 KYC 檔案</b>
     */
    public static UserIdentity initializeDraft(String userId, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("TenantId is strictly required for KYC data isolation.");
        }
        return new UserIdentity(userId, tenantId, null, null, null, null, KycStatus.UNVERIFIED, null, null);
    }

    // ── 核心狀態機 ──

    /**
     * <b>【業務變更】使用者提交 KYC 資料進行審核</b>
     */
    public void submitForVerification(NationalId id, RealName name, LocalDate dob, Address address) {
        if (this.status == KycStatus.VERIFIED) {
            throw new IllegalStateException("帳號已通過實名認證，不可任意修改身分資料。");
        }
        if (this.status == KycStatus.PENDING_REVIEW) {
            throw new IllegalStateException("資料正在審核中，請勿重複提交。");
        }

        this.nationalId = id;
        this.realName = name;
        this.dateOfBirth = dob;
        this.residentialAddress = address;

        KycStatus oldStatus = this.status;
        this.status = KycStatus.PENDING_REVIEW;

        // 內部自帶 tenantId，不再依賴外部傳入，保證發射事件的租戶正確性
        this.registerStatusChangedEvent(oldStatus, this.status, null);
    }

    /**
     * <b>【管理行為】審核通過</b>
     */
    public void approve(String reviewerId) {
        if (this.status != KycStatus.PENDING_REVIEW) {
            throw new IllegalStateException("只能核准狀態為『審核中』的檔案。");
        }

        KycStatus oldStatus = this.status;
        this.status = KycStatus.VERIFIED;
        this.lastAudit = new VerificationAudit(reviewerId, LocalDateTime.now(), "Approved");

        this.registerStatusChangedEvent(oldStatus, this.status, null);
    }

    /**
     * <b>【管理行為】審核退回</b>
     */
    public void reject(String reviewerId, String reason) {
        if (this.status != KycStatus.PENDING_REVIEW) {
            throw new IllegalStateException("只能退回狀態為『審核中』的檔案。");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("退回必須填寫明確的原因，供使用者修正。");
        }

        KycStatus oldStatus = this.status;
        this.status = KycStatus.REJECTED;
        this.lastAudit = new VerificationAudit(reviewerId, LocalDateTime.now(), reason);

        this.registerStatusChangedEvent(oldStatus, this.status, reason);
    }

    // ── 內部防腐與事件拉取 ──

    private void registerStatusChangedEvent(KycStatus oldStatus, KycStatus newStatus, String rejectReason) {
        this.domainEvents.add(new KycStatusChangedEvent(
                this.tenantId, // 統一從聚合根內部屬性獲取，杜絕參數傳遞錯誤
                this.id,
                oldStatus.name(),
                newStatus.name(),
                rejectReason,
                this.version
        ));
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }

    // ── Getters (防禦外部竄改) ──
    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public NationalId getNationalId() { return nationalId; }
    public RealName getRealName() { return realName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Address getResidentialAddress() { return residentialAddress; }
    public KycStatus getStatus() { return status; }
    public VerificationAudit getLastAudit() { return lastAudit; }
    public Long getVersion() { return version; }
}