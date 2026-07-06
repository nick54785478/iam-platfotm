package com.example.demo.iface.rest;

import com.example.demo.application.service.UserIdentityCommandService;
import com.example.demo.application.shared.command.inbound.ApproveKycCommand;
import com.example.demo.application.shared.command.inbound.RejectKycCommand;
import com.example.demo.application.shared.command.inbound.SubmitKycCommand;
import com.example.demo.iface.dto.req.RejectKycResource;
import com.example.demo.iface.dto.req.SubmitKycResource;
import com.example.demo.iface.dto.res.KycApprovedResource;
import com.example.demo.iface.dto.res.KycRejectedResource;
import com.example.demo.iface.dto.res.KycSubmittedResource;
import com.example.demo.infra.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycCommandController {

    private final UserIdentityCommandService kycCommandService;

    /**
     * 提交個人資訊
     */
    @PostMapping("/submit")
    public ResponseEntity<KycSubmittedResource> submitKyc(@RequestHeader("X-User-Id") String userId,
                                                          @RequestBody SubmitKycResource request) {

        // 1. 在邊界處安全獲取租戶上下文
        String currentTenantId = TenantContext.getCurrentTenantId();
        System.out.println("tenantId: " + currentTenantId);

        // 2. 將 Request + Context 轉譯組裝為強型別 Command
        SubmitKycCommand command = new SubmitKycCommand(currentTenantId, userId, request.firstName(),
                request.lastName(), request.idNumber(), request.countryCode(), request.documentType(),
                LocalDate.parse(request.dateOfBirth()), // 在邊界完成型別轉換
                request.addrCountry(), request.addrState(), request.addrCity(), request.addrPostalCode(),
                request.addrDetail());

        // 3. 投遞指令給應用層
        kycCommandService.submitKyc(command);
        return new ResponseEntity<>(new KycSubmittedResource("200", "提交成功"), HttpStatus.CREATED);
    }

    /**
     * <b>【API】核心審核：核准通過</b>
     *
     * @param targetUserId 要審核的目標用戶 ID (來自 URL 參數)
     * @param reviewerId   執行審核動作的管理員 ID (來自 Token)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<KycApprovedResource> approveKyc(@PathVariable("id") String targetUserId,
                                                          @RequestHeader("X-User-Id") String reviewerId) {

        String currentTenantId = TenantContext.getCurrentTenantId();

        ApproveKycCommand command = new ApproveKycCommand(currentTenantId, targetUserId, reviewerId);

        kycCommandService.approveKyc(command);
        return ResponseEntity.ok(new KycApprovedResource("200", "核准通過"));
    }

    /**
     * <b>【API】核心審核：退回修正</b>
     *
     * @param targetUserId 要審核的目標用戶 ID (來自 URL 參數)
     * @param reviewerId   執行審核動作的管理員 ID (來自 Token)
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<KycRejectedResource> rejectKyc(@PathVariable("id") String targetUserId,
                                                         @RequestHeader("X-User-Id") String reviewerId, @RequestBody RejectKycResource resource) {

        String currentTenantId = TenantContext.getCurrentTenantId();

        RejectKycCommand command = new RejectKycCommand(currentTenantId, targetUserId, reviewerId, resource.reason());

        kycCommandService.rejectKyc(command);
        return ResponseEntity.ok(new KycRejectedResource("200", "駁回，請修正"));
    }
}