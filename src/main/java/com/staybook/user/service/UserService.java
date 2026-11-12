package com.staybook.user.service;

import com.staybook.common.crypto.LookupHasher;
import com.staybook.common.exception.BaseException;
import com.staybook.common.security.UserRole;
import com.staybook.user.domain.User;
import com.staybook.user.domain.UserPersonalInfo;
import com.staybook.user.dto.RegisterCommand;
import com.staybook.user.dto.UpdateMeRequest;
import com.staybook.user.dto.UserAccount;
import com.staybook.user.dto.UserMeResponse;
import com.staybook.user.exception.UserErrorCode;
import com.staybook.user.repository.UserPersonalInfoRepository;
import com.staybook.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    /** 이 나이 미만은 가입할 수 없다 (법정대리인 동의 절차가 1차에 없음). */
    public static final int MIN_SIGNUP_AGE = 14;
    /** 이 나이 이상이어야 예약할 수 있다. */
    public static final int ADULT_AGE = 19;

    private final UserRepository userRepository;
    private final UserPersonalInfoRepository personalInfoRepository;
    private final LookupHasher lookupHasher;
    private final TermsAgreementHandler termsAgreementHandler;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // ---------- 가입 (auth가 호출) ----------

    /** 이메일 가입. USERS, USER_PERSONAL_INFO를 만들고 약관 동의를 기록한다. */
    @Transactional
    public Long register(RegisterCommand command) {
        validateSignupAge(command.birthDate());
        String email = normalizeEmail(command.email());
        String emailHash = lookupHasher.hashEmail(email);
        if (personalInfoRepository.existsByEmailHash(emailHash)) {
            throw new BaseException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String phone = blankToNull(command.phone());

        User user = userRepository.save(User.create(now()));
        saveInfo(UserPersonalInfo.create(user.getId(), command.name().trim(), email, emailHash,
                phone, phone == null ? null : lookupHasher.hashPhone(phone), command.birthDate()));
        termsAgreementHandler.agree(user.getId(), command.agreedTermIds());
        return user.getId();
    }

    /**
     * 소셜 최초 로그인. 생년월일과 약관 동의가 없는 가입 미완료 상태로 만든다.
     * email은 제공자가 주지 않으면 null이다.
     */
    @Transactional
    public Long registerSocial(String name, String email) {
        String normalized = email == null || email.isBlank() ? null : normalizeEmail(email);
        User user = userRepository.save(User.create(now()));
        saveInfo(UserPersonalInfo.create(user.getId(), name, normalized,
                normalized == null ? null : lookupHasher.hashEmail(normalized), null, null, null));
        return user.getId();
    }

    /** 가입 미완료 회원이 생년월일과 약관 동의를 입력해 가입을 끝낸다. */
    @Transactional
    public void completeSignup(Long userId, LocalDate birthDate, List<Long> agreedTermIds) {
        UserPersonalInfo info = getActiveInfo(userId);
        if (info.isSignupCompleted()) {
            throw new BaseException(UserErrorCode.SIGNUP_ALREADY_COMPLETED);
        }
        validateSignupAge(birthDate);
        info.changeBirthDate(birthDate);
        termsAgreementHandler.agree(userId, agreedTermIds);
    }

    // ---------- 다른 feature용 조회·변경 ----------

    /** 탈퇴 회원의 이메일도 포함해 이미 쓰인 이메일인지 본다. */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return personalInfoRepository.existsByEmailHash(lookupHasher.hashEmail(email));
    }

    /** 탈퇴하지 않은 회원만 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<UserAccount> findAccountByEmail(String email) {
        return personalInfoRepository.findByEmailHash(lookupHasher.hashEmail(email))
                .flatMap(info -> findAccount(info.getUserId()));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findAccount(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .map(user -> new UserAccount(user.getId(), user.getRole(),
                        personalInfoRepository.findById(userId)
                                .map(UserPersonalInfo::isSignupCompleted).orElse(false)));
    }

    @Transactional(readOnly = true)
    public Optional<String> findEmail(Long userId) {
        return personalInfoRepository.findById(userId).map(UserPersonalInfo::getEmail);
    }

    /** 회원 이름. 리뷰 작성자 표시처럼 탈퇴 여부와 관계없이 이름이 필요할 때 쓴다. */
    @Transactional(readOnly = true)
    public Optional<String> findName(Long userId) {
        return personalInfoRepository.findById(userId).map(UserPersonalInfo::getName);
    }

    /** 성인 여부. 생년월일이 없는(가입 미완료) 회원은 성인으로 보지 않는다. */
    @Transactional(readOnly = true)
    public boolean isAdult(Long userId) {
        LocalDate birthDate = getActiveInfo(userId).getBirthDate();
        return birthDate != null && age(birthDate) >= ADULT_AGE;
    }

    @Transactional
    public void changeRole(Long userId, UserRole role) {
        getActiveUser(userId).changeRole(role, now());
    }

    // ---------- /api/users/me ----------

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        User user = getActiveUser(userId);
        return toResponse(user, getActiveInfo(userId));
    }

    @Transactional
    public UserMeResponse updateMe(Long userId, UpdateMeRequest request) {
        User user = getActiveUser(userId);
        UserPersonalInfo info = getActiveInfo(userId);
        if (request.name() != null) {
            info.changeName(request.name().trim());
        }
        if (request.phone() != null) {
            info.changePhone(request.phone(), lookupHasher.hashPhone(request.phone()));
        }
        return toResponse(user, info);
    }

    /** soft delete. 리프레시 토큰은 이벤트를 받은 auth가 전부 지운다. */
    @Transactional
    public void withdraw(Long userId) {
        getActiveUser(userId).withdraw(now());
        eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
    }

    // ---------- 내부 ----------

    private void saveInfo(UserPersonalInfo info) {
        try {
            personalInfoRepository.saveAndFlush(info);
        } catch (DataIntegrityViolationException e) {
            // 같은 이메일로 동시에 가입한 경우 유니크 제약에 걸린다
            throw new BaseException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void validateSignupAge(LocalDate birthDate) {
        if (birthDate == null || birthDate.isAfter(LocalDate.now(clock))) {
            throw new BaseException(UserErrorCode.INVALID_BIRTH_DATE);
        }
        if (age(birthDate) < MIN_SIGNUP_AGE) {
            throw new BaseException(UserErrorCode.UNDER_AGE);
        }
    }

    /** 만 나이. */
    private int age(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now(clock)).getYears();
    }

    private User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BaseException(UserErrorCode.USER_NOT_FOUND));
    }

    private UserPersonalInfo getActiveInfo(Long userId) {
        getActiveUser(userId);
        return personalInfoRepository.findById(userId)
                .orElseThrow(() -> new BaseException(UserErrorCode.USER_NOT_FOUND));
    }

    private UserMeResponse toResponse(User user, UserPersonalInfo info) {
        return new UserMeResponse(user.getId(), info.getName(), info.getEmail(), info.getPhone(),
                info.getBirthDate(), user.getRole(), info.isSignupCompleted());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
