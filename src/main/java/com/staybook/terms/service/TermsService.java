package com.staybook.terms.service;

import com.staybook.common.exception.BaseException;
import com.staybook.terms.domain.Term;
import com.staybook.terms.domain.UserTermAgreement;
import com.staybook.terms.dto.TermResponse;
import com.staybook.terms.exception.TermsErrorCode;
import com.staybook.terms.repository.TermRepository;
import com.staybook.terms.repository.UserTermAgreementRepository;
import com.staybook.user.service.TermsAgreementHandler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermsService implements TermsAgreementHandler {

    private final TermRepository termRepository;
    private final UserTermAgreementRepository agreementRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TermResponse> getCurrentTerms() {
        return currentTerms().stream().map(TermResponse::from).toList();
    }

    @Override
    @Transactional
    public void agree(Long userId, List<Long> agreedTermIds) {
        List<Term> current = currentTerms();
        Set<Long> agreed = agreedTermIds == null ? Set.of() : new HashSet<>(agreedTermIds);
        Set<Long> currentIds = current.stream().map(Term::getId).collect(Collectors.toSet());

        if (!currentIds.containsAll(agreed)) {
            // 없는 약관이거나 지난 버전에 동의한 요청
            throw new BaseException(TermsErrorCode.INVALID_TERM);
        }
        boolean missingRequired = current.stream()
                .anyMatch(term -> term.isRequired() && !agreed.contains(term.getId()));
        if (missingRequired) {
            throw new BaseException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        // 선택 약관을 거절한 것도 버전별로 남긴다
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, UserTermAgreement> existing = agreementRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserTermAgreement::getTermId, Function.identity()));
        for (Term term : current) {
            boolean isAgreed = agreed.contains(term.getId());
            UserTermAgreement agreement = existing.get(term.getId());
            if (agreement == null) {
                agreementRepository.save(UserTermAgreement.create(userId, term.getId(), isAgreed, now));
            } else {
                agreement.change(isAgreed, now);
            }
        }
    }

    /** 종류별로 시행일이 지난 것 중 가장 최근 버전. */
    private List<Term> currentTerms() {
        Comparator<Term> byNewest = Comparator.comparing(Term::getEffectiveAt).thenComparing(Term::getId);
        return termRepository.findByEffectiveAtLessThanEqual(LocalDateTime.now(clock)).stream()
                .collect(Collectors.toMap(Term::getType, Function.identity(), BinaryOperator.maxBy(byNewest)))
                .values().stream()
                .sorted(Comparator.comparing(Term::getId))
                .toList();
    }
}
