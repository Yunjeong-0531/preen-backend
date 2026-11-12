package com.staybook.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** 엔티티의 @CreatedDate, @LastModifiedDate를 채운다. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
