package com.example.demo.infra.projection.view;

import com.example.demo.application.domain.user.aggregate.vo.NationalId;
import com.example.demo.infra.persistence.converter.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 如果去問 CQRS 的發明者 Greg Young，嚴格的定義是：「實體分離」。
 * Command 寫入關聯式資料庫（或 Event Store），然後透過 Message Broker（如 Kafka）
 * 把資料非同步抄寫到另一個物理上完全獨立的 Query 資料庫（例如 Elasticsearch 或 MongoDB）。
 *
 * 在這個定義下，我們的 Query 側去讀 Command 側的表，確實會被視為「犯規」。
 * <p>
 * 但現實是，完全的物理分離會帶來極高的維護成本、資料延遲（Eventual Consistency）以及雙倍的儲存成本。
 * 特別是在處理高度機密的 PII (個人身分資訊) 時，把完整的明文身分證到處抄寫，資安風險極高。
 * <p>
 * 目前絕大多數的微服務架構採用的是 Martin Fowler 等人提倡的務實作法：「邏輯分離」。
 * 在 Logical CQRS 中，實體資料庫可以是同一個，但「程式碼的邏輯模型」必須絕對切開。
 */
@Entity
@Table(name = "user_identities")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycPersonalSourceView {

    @Id
    @Column(name = "id", updatable = false)
    private String id;

    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id_number", updatable = false)
    private String nationalIdNumber;

    // 新增：發照國家
    @Column(name = "national_id_country", length = 2, updatable = false)
    private String nationalIdCountry;

    @Enumerated(EnumType.STRING)
    @Column(name = "national_id_type", updatable = false)
    private NationalId.DocumentType nationalIdType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "first_name", updatable = false)
    private String firstName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "last_name", updatable = false)
    private String lastName;

    @Column(name = "date_of_birth", updatable = false)
    private LocalDate dateOfBirth;

    // 新增：地址相關欄位
    @Column(name = "addr_country", length = 2, updatable = false)
    private String addrCountry;

    @Column(name = "addr_state", updatable = false)
    private String addrState;

    @Column(name = "addr_city", updatable = false)
    private String addrCity;

    @Column(name = "addr_postal_code", updatable = false)
    private String addrPostalCode;

    @Convert(converter = EncryptedStringConverter.class) // 地址細節同樣需要解密保護
    @Column(name = "addr_detail", updatable = false)
    private String addrDetail;

    @Column(name = "status", updatable = false)
    private String status;

    @Column(name = "review_comments", length = 500, updatable = false)
    private String reviewComments;

    @Column(name = "reviewer_id", updatable = false)
    private String reviewerId;

    @Column(name = "reviewed_at", updatable = false)
    private LocalDateTime reviewedAt;
}