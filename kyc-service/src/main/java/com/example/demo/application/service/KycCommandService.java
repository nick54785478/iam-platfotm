package com.example.demo.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.user.aggregate.UserIdentity;
import com.example.demo.application.domain.user.aggregate.vo.Address;
import com.example.demo.application.domain.user.aggregate.vo.NationalId;
import com.example.demo.application.domain.user.aggregate.vo.RealName;
import com.example.demo.application.port.KycCommandRepositoryPort;
import com.example.demo.application.shared.command.inbound.ApproveKycCommand;
import com.example.demo.application.shared.command.inbound.RejectKycCommand;
import com.example.demo.application.shared.command.inbound.SubmitKycCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>[應用層 - 服務] KYC 寫入端應用程式服務</h2>
 * <p>
 * 專責處理資料庫事務編排、聚合根生命週期加載，不包含任何核心領域規則。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycCommandService {

	private final KycCommandRepositoryPort commandRepository;

	/**
	 * <b>【核心編排】現在只接受強型別指令，徹底與 Transport 層隔離</b>
	 */
	@Transactional
	public void submitKyc(SubmitKycCommand command) {
		log.info("[Kyc-Application] 接收到強型別指令，開始編排業務. Tenant: {}, User: {}", command.tenantId(), command.userId());

		// 1. 載入或初始化聚合根 (全資料靠 Command 供應)
		UserIdentity userIdentity = commandRepository.findById(command.userId())
				.orElseGet(() -> UserIdentity.initializeDraft(command.userId(), command.tenantId()));

		// 2. 組裝內圈不可變值物件
		NationalId nationalId = new NationalId(command.idNumber(), command.countryCode(), command.documentType());

		String fullName = command.firstName() + " " + command.lastName();
		RealName realName = new RealName(command.firstName(), command.lastName(), fullName);

		Address address = new Address(command.addrCountry(), command.addrState(), command.addrCity(),
				command.addrPostalCode(), command.addrDetail());

		// 3. 驅動狀態機
		userIdentity.submitForVerification(nationalId, realName, command.dateOfBirth(), address);

		// 4. 持久化 (Transaction 內包含本地投影與 Outbox 寫入)
		commandRepository.save(userIdentity);
		log.info("[Kyc-Application] KYC 指令執行成功. User: {}", command.userId());
	}

	/**
     * <b>【核心編排】管理員/系統核准審查</b>
     */
    @Transactional
    public void approveKyc(ApproveKycCommand command) {
        log.info("[Kyc-Application] 核准審查操作. Reviewer: {}, TargetUser: {}", command.reviewerId(), command.targetUserId());

        UserIdentity userIdentity = commandRepository.findById(command.targetUserId())
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者的身分檔案：" + command.targetUserId()));

        // 核心防禦：確保執行審核的主管與被審核的員工屬於同一個租戶
        if (!userIdentity.getTenantId().equals(command.tenantId())) {
            throw new SecurityException("資安攔截：無權跨租戶審核資料！");
        }

        userIdentity.approve(command.reviewerId());
        commandRepository.save(userIdentity);
    }

    /**
     * <b>【核心編排】管理員/系統退回審查</b>
     */
    @Transactional
    public void rejectKyc(RejectKycCommand command) {
        log.info("[Kyc-Application] 退回審查操作. Reviewer: {}, TargetUser: {}, 原因: {}", 
                 command.reviewerId(), command.targetUserId(), command.reason());

        UserIdentity userIdentity = commandRepository.findById(command.targetUserId())
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者的身分檔案：" + command.targetUserId()));

        // 核心防禦：確保執行審核的主管與被審核的員工屬於同一個租戶
        if (!userIdentity.getTenantId().equals(command.tenantId())) {
            throw new SecurityException("資安攔截：無權跨租戶退回資料！");
        }

        userIdentity.reject(command.reviewerId(), command.reason());
        commandRepository.save(userIdentity);
    }
}