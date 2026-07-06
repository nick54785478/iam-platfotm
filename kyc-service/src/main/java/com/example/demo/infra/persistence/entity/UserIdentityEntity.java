package com.example.demo.infra.persistence.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.demo.application.domain.user.aggregate.UserIdentity;
import com.example.demo.application.domain.user.aggregate.vo.Address;
import com.example.demo.application.domain.user.aggregate.vo.KycStatus;
import com.example.demo.application.domain.user.aggregate.vo.NationalId;
import com.example.demo.application.domain.user.aggregate.vo.RealName;
import com.example.demo.application.domain.user.aggregate.vo.VerificationAudit;
import com.example.demo.infra.persistence.converter.EncryptedStringConverter; // 假設你實作的 AES 轉換器

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * <h2>[基礎設施層] KYC 身分資料庫實體</h2>
 * <p>
 * 採用嚴格封裝，僅透過 fromDomain 與 updateFromDomain 改變狀態。
 * </p>
 */
@Entity
@Table(name = "user_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 🛡️ 僅供 Hibernate 反射實例化使用，外部禁用
public class UserIdentityEntity implements Persistable<String> {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private String id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	// --- 身分證資料 (高度機密，套用自動加密) ---
	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "national_id_number")
	private String nationalIdNumber;

	@Column(name = "national_id_country", length = 2)
	private String nationalIdCountry;

	@Enumerated(EnumType.STRING)
	@Column(name = "national_id_type")
	private NationalId.DocumentType nationalIdType;

	// --- 真實姓名 (機密，套用自動加密) ---
	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "first_name")
	private String firstName;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "last_name")
	private String lastName;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "full_name")
	private String fullName;

	// --- 其他 PII 資料 ---
	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(name = "addr_country", length = 2)
	private String addrCountry;

	@Column(name = "addr_state")
	private String addrState;

	@Column(name = "addr_city")
	private String addrCity;

	@Column(name = "addr_postal_code")
	private String addrPostalCode;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "addr_detail")
	private String addrDetail;

	// --- 狀態機與審計 ---
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private KycStatus status;

	@Column(name = "reviewer_id")
	private String reviewerId;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	@Column(name = "review_comments", length = 500)
	private String reviewComments;

	@Version
	@Column(name = "version")
	private Long version;

	// ===================================================================
	// 🚀 2. 解決 Spring Data JPA 誤判的核心防線
	// ===================================================================

	@Transient // 不映射到資料庫欄位
	private boolean isNewEntity;

	/**
	 * 覆寫 Spring Data 的判斷邏輯
	 */
	@Override
	public boolean isNew() {
		return this.isNewEntity;
	}

	/**
	 * 資料庫操作完成或載入後，重置新舊狀態
	 */
	@PostLoad
	@PostPersist
	@PostUpdate
	private void markNotNew() {
		this.isNewEntity = false;
	}

	/**
	 * 私有建構子：確保只能透過工廠方法建立
	 */
	private UserIdentityEntity(String id, String tenantId) {
		this.id = id;
		this.tenantId = tenantId;
	}

	// ── 防腐層映射邏輯 (Mapping) ──

	/**
	 * <b>【實體工廠】將領域物件轉化為新的資料庫實體</b>
	 */
	public static UserIdentityEntity fromDomain(UserIdentity domain) {
		UserIdentityEntity entity = new UserIdentityEntity(domain.getId(), domain.getTenantId());

		// 3. 關鍵操作：因為是從無到有建立，明確強制指示 JPA 這是一筆新資料，請執行 INSERT！
		entity.isNewEntity = true;

		entity.updateFromDomain(domain);
		return entity;
	}

	/**
	 * <b>【狀態同步】將領域物件的變更覆寫至現有資料庫實體</b>
	 */
	public void updateFromDomain(UserIdentity domain) {
		// 映射 PII (需防禦 Null)
		if (domain.getNationalId() != null) {
			this.nationalIdNumber = domain.getNationalId().idNumber();
			this.nationalIdCountry = domain.getNationalId().countryCode();
			this.nationalIdType = domain.getNationalId().documentType();
		}
		if (domain.getRealName() != null) {
			this.firstName = domain.getRealName().firstName();
			this.lastName = domain.getRealName().lastName();
			this.fullName = domain.getRealName().fullName();
		}
		if (domain.getResidentialAddress() != null) {
			this.addrCountry = domain.getResidentialAddress().countryCode();
			this.addrState = domain.getResidentialAddress().stateOrProvince();
			this.addrCity = domain.getResidentialAddress().city();
			this.addrPostalCode = domain.getResidentialAddress().postalCode();
			this.addrDetail = domain.getResidentialAddress().detailLine();
		}

		this.dateOfBirth = domain.getDateOfBirth();
		this.status = domain.getStatus();

		// 映射審計軌跡
		if (domain.getLastAudit() != null) {
			this.reviewerId = domain.getLastAudit().reviewerId();
			this.reviewedAt = domain.getLastAudit().reviewedAt();
			this.reviewComments = domain.getLastAudit().comments();
		}

		this.version = domain.getVersion();
	}

	/**
	 * <b>【領域還原】將資料庫實體重建回領域聚合根</b>
	 */
	public UserIdentity toDomain() {
		NationalId nId = (this.nationalIdNumber != null)
				? new NationalId(this.nationalIdNumber, this.nationalIdCountry, this.nationalIdType)
				: null;

		RealName rName = (this.firstName != null || this.lastName != null || this.fullName != null)
				? new RealName(this.firstName, this.lastName, this.fullName)
				: null;

		Address address = (this.addrCountry != null)
				? new Address(this.addrCountry, this.addrState, this.addrCity, this.addrPostalCode, this.addrDetail)
				: null;

		VerificationAudit audit = (this.reviewerId != null)
				? new VerificationAudit(this.reviewerId, this.reviewedAt, this.reviewComments)
				: null;

		// 利用聚合根的重建建構式
		return new UserIdentity(this.id, this.tenantId, nId, rName, this.dateOfBirth, address, this.status, audit,
				this.version);
	}
}