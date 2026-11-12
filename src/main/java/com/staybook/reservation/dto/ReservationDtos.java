package com.staybook.reservation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** reservation 요청·응답 모음. */
public final class ReservationDtos {

    private ReservationDtos() {
    }

    /** guestName, guestPhone을 보내지 않으면 회원 정보의 이름과 전화번호를 쓴다. */
    public record CreateRequest(
            @NotNull(message = "객실을 선택해 주세요.") Long roomId,
            @NotNull(message = "체크인 날짜를 입력해 주세요.") LocalDate checkIn,
            @NotNull(message = "체크아웃 날짜를 입력해 주세요.") LocalDate checkOut,
            @NotNull @Min(value = 1, message = "인원은 1명 이상이어야 합니다.")
            @Max(value = 100, message = "인원이 너무 많습니다.") Integer guestCount,
            @Size(max = 50) @Pattern(regexp = ".*\\S.*", message = "예약자 이름을 입력해 주세요.") String guestName,
            @Pattern(regexp = "^[0-9\\-+ ]{9,20}$", message = "전화번호 형식이 올바르지 않습니다.") String guestPhone
    ) {
    }

    public record PriceItemResponse(String itemType, String name, BigDecimal amount) {
    }

    /**
     * @param holdExpiresAt PENDING 상태에서 결제를 마쳐야 하는 시각
     * @param refundPolicy  예약 시점의 환불정책 스냅샷
     */
    public record ReservationResponse(
            Long id,
            String reservationNo,
            Long accommodationId,
            String accommodationName,
            Long roomId,
            String roomName,
            LocalDate checkIn,
            LocalDate checkOut,
            int guestCount,
            String guestName,
            String guestPhone,
            BigDecimal totalPrice,
            String status,
            LocalDateTime holdExpiresAt,
            LocalDateTime cancelledAt,
            LocalDateTime createdAt,
            List<PriceItemResponse> priceItems,
            JsonNode refundPolicy
    ) {
    }

    /**
     * 취소 결과.
     *
     * @param refundRate     적용된 환불 비율 (0~1). 결제 전 취소면 null
     * @param refundAmount   환불 예정액. 결제 전 취소면 0
     * @param penaltyAmount  위약금. 결제 전 취소면 0
     */
    public record CancelResponse(ReservationResponse reservation, BigDecimal refundRate, BigDecimal refundAmount,
                                 BigDecimal penaltyAmount) {
    }

    /** payment가 결제 준비·승인 검증에 쓰는 요약. */
    public record PaymentInfo(Long reservationId, String reservationNo, Long userId, BigDecimal totalPrice,
                              String status, boolean payable, String title) {
    }

    /** review가 작성 조건 검사에 쓰는 요약. */
    public record StayInfo(Long reservationId, Long userId, Long accommodationId, String status,
                           boolean completed) {
    }
}
