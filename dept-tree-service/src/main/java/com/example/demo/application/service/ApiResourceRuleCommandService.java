package com.example.demo.application.service;


import com.example.demo.application.port.ApiResourceRuleWriterPort;
import com.example.demo.application.shared.command.inbound.CreateApiRuleCommand;
import com.example.demo.application.shared.command.inbound.UpdateApiRuleCommand;
import com.example.demo.application.shared.event.ApiRuleChangedEvent;
import com.example.demo.infra.apirule.ApiResourceRule;
import com.example.demo.infra.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>[應用層] API 動態資源規則維護服務 (Command Service)</h2>
 * <p>
 * <b>【架構修正】</b>：徹底剔除對 Query Service 的依賴，改用 Event Publisher 解耦快取清除邏輯。<br>
 * <b>【事件升級】</b>：全面適配 OutboundEvent 規格，精準注入 tenantId 以支援未來的跨服務廣播。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiResourceRuleCommandService {

    private final ApiResourceRuleWriterPort writerPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long createRule(CreateApiRuleCommand cmd) {

        // 優先使用 Command 傳遞的 TenantId，若無則預設為 "SYSTEM"
        String currentTenant = (cmd.tenantId() != null && !cmd.tenantId().isBlank())
                ? cmd.tenantId()
                : "SYSTEM";


        // 將 currentTenant 傳入工廠方法
        ApiResourceRule newRule = ApiResourceRule.createNew(
                currentTenant, cmd.httpMethod(), cmd.pathPattern(), cmd.requiredPermission(), cmd.priority()
        );
        writerPort.save(newRule);

        eventPublisher.publishEvent(ApiRuleChangedEvent.of(currentTenant, "CREATE", newRule.getId()));
        return newRule.getId();
    }

    @Transactional
    public void updateRule(UpdateApiRuleCommand cmd) {

        // IDOR 終極防禦：用 ID + TenantId 雙重條件撈取實體！
        ApiResourceRule rule = writerPort.findByIdAndTenantId(cmd.ruleId(), cmd.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("找不到該 API 規則，或您無權修改此租戶的資源: " + cmd.ruleId()));

        rule.update(cmd.httpMethod(), cmd.pathPattern(), cmd.requiredPermission(), cmd.priority());
        writerPort.save(rule);

        log.info("[Authz-Admin] 成功更新 API 保護規則 ID: {} (租戶: {})", cmd.ruleId(), cmd.tenantId());

        // 發布整合事件 (確保帶入正確的 TenantId)
        eventPublisher.publishEvent(ApiRuleChangedEvent.of(cmd.tenantId(), "UPDATE", rule.getId()));
    }

    @Transactional
    public void toggleRuleStatus(String tenantId, Long ruleId, boolean isActive, String operator) {

        // IDOR 終極防禦
        ApiResourceRule rule = writerPort.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該 API 規則，或您無權修改此租戶的資源: " + ruleId));

        rule.toggleActiveStatus(isActive);
        // 如果實體有實作 updatedBy，也可以在這裡呼叫 rule.setUpdatedBy(operator)
        writerPort.save(rule);

        log.info("[Authz-Admin] API 規則 ID: {} 狀態已變更為: {} (租戶: {})", ruleId, isActive ? "啟用" : "停用", tenantId);

        // 發布整合事件
        eventPublisher.publishEvent(ApiRuleChangedEvent.of(tenantId, "TOGGLE", rule.getId()));
    }
}