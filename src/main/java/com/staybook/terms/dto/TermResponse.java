package com.staybook.terms.dto;

import com.staybook.terms.domain.Term;
import java.time.LocalDateTime;

public record TermResponse(
        Long id,
        String type,
        String version,
        String content,
        boolean required,
        LocalDateTime effectiveAt
) {

    public static TermResponse from(Term term) {
        return new TermResponse(term.getId(), term.getType(), term.getVersion(), term.getContent(),
                term.isRequired(), term.getEffectiveAt());
    }
}
