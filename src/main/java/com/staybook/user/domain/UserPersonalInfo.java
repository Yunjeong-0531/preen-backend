package com.staybook.user.domain;

import com.staybook.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이메일·전화번호는 암호화해서 저장하고, 조회는 해시 컬럼으로 한다.
 */
@Entity
@Table(name = "user_personal_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPersonalInfo {

    @Id
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String email;

    @Column(columnDefinition = "char(64)")
    private String emailHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String phone;

    @Column(columnDefinition = "char(64)")
    private String phoneHash;

    /** 소셜 가입 후 추가 입력 전에는 null이다. */
    private LocalDate birthDate;

    public static UserPersonalInfo create(Long userId, String name, String email, String emailHash,
                                          String phone, String phoneHash, LocalDate birthDate) {
        UserPersonalInfo info = new UserPersonalInfo();
        info.userId = userId;
        info.name = name;
        info.email = email;
        info.emailHash = emailHash;
        info.phone = phone;
        info.phoneHash = phoneHash;
        info.birthDate = birthDate;
        return info;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePhone(String phone, String phoneHash) {
        this.phone = phone;
        this.phoneHash = phoneHash;
    }

    public void changeBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    /** 생년월일까지 입력해야 가입이 끝난 것으로 본다. */
    public boolean isSignupCompleted() {
        return birthDate != null;
    }
}
