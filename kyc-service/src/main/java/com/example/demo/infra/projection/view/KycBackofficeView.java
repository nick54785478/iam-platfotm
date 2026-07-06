package com.example.demo.infra.projection.view;

import java.time.LocalDateTime;

import com.example.demo.infra.persistence.converter.EncryptedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "kyc_backoffice_views", indexes = {
		@Index(name = "idx_kyc_view_tenant_status", columnList = "tenant_id, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycBackofficeView {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private String id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "full_name")
	private String fullName;

	@Column(name = "masked_national_id")
	private String maskedNationalId;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "reject_reason")
	private String rejectReason;

	@Column(name = "last_updated_at", nullable = false)
	private LocalDateTime lastUpdatedAt;

	// 🚀 依然保留 version 欄位以滿足 DB 的 Not Null 約束
	@Column(name = "version", nullable = false)
	private Long version;

	public static KycBackofficeView createNew(
			String id, String tenantId, String fullName, String maskedNationalId,
			String status, String rejectReason, Long version) {

		KycBackofficeView view = new KycBackofficeView();
		view.id = id;
		view.tenantId = tenantId;
		view.fullName = fullName;
		view.maskedNationalId = maskedNationalId;
		view.status = status;
		view.rejectReason = rejectReason;
		view.lastUpdatedAt = LocalDateTime.now();
		view.version = version;
		return view;
	}

	/**
	 * <b>【視圖同步】更新狀態與資料</b>
	 * 🚀 移除 eventVersion 判斷，因為本地同事務投影必定是最新的
	 */
	public boolean syncDetails(
			String fullName, String maskedNationalId, String status,
			String rejectReason, Long eventVersion) {

		this.fullName = fullName;
		this.maskedNationalId = maskedNationalId;
		this.status = status;
		this.rejectReason = rejectReason;
		this.lastUpdatedAt = LocalDateTime.now();
		this.version = eventVersion; // 直接覆寫以保持兩邊數字一致（雖然邏輯上已不需要依賴它防禦）

		return true;
	}
}