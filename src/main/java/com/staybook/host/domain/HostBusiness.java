package com.staybook.host.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "host_businesses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HostBusiness {

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hostUserId;

    @Column(nullable = false, length = 100)
    private String businessName;

    @Column(nullable = false, length = 20)
    private String businessRegistrationNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 1차는 심사 없이 바로 승인 상태로 만든다. approval_status는 이후 심사 기능을 위해 남겨 둔다. */
    public static HostBusiness approved(Long hostUserId, String businessName, String registrationNo,
                                        LocalDateTime now) {
        HostBusiness business = new HostBusiness();
        business.hostUserId = hostUserId;
        business.businessName = businessName;
        business.businessRegistrationNo = registrationNo;
        business.approvalStatus = ApprovalStatus.APPROVED;
        business.createdAt = now;
        return business;
    }
}
