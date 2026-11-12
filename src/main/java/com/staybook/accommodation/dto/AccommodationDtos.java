package com.staybook.accommodation.dto;

import com.staybook.accommodation.domain.Accommodation.ListingStatus;
import com.staybook.accommodation.domain.Room.RoomStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/** accommodation 요청·응답 모음. */
public final class AccommodationDtos {

    private static final String BLANK_ONLY = ".*\\S.*";

    private AccommodationDtos() {
    }

    // ---------- 요청 ----------

    public record AccommodationCreateRequest(
            @NotNull(message = "지역을 선택해 주세요.") Long regionId,
            @NotBlank(message = "숙소 이름을 입력해 주세요.") @Size(max = 100) String name,
            @Size(max = 5000) String description,
            @NotBlank(message = "주소를 입력해 주세요.") @Size(max = 255) String address,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotNull(message = "체크인 시각을 입력해 주세요.") LocalTime checkInTime,
            @NotNull(message = "체크아웃 시각을 입력해 주세요.") LocalTime checkOutTime,
            @Size(max = 20, message = "이미지는 20장까지 등록할 수 있습니다.")
            List<@NotBlank @Size(max = 500) String> imageUrls,
            List<@NotNull Long> amenityIds
    ) {
    }

    /** 보내지 않은(null) 항목은 바꾸지 않는다. imageUrls, amenityIds는 보내면 통째로 교체한다. */
    public record AccommodationUpdateRequest(
            Long regionId,
            @Size(max = 100) @Pattern(regexp = BLANK_ONLY, message = "숙소 이름을 입력해 주세요.") String name,
            @Size(max = 5000) String description,
            @Size(max = 255) @Pattern(regexp = BLANK_ONLY, message = "주소를 입력해 주세요.") String address,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            @Size(max = 20, message = "이미지는 20장까지 등록할 수 있습니다.")
            List<@NotBlank @Size(max = 500) String> imageUrls,
            List<@NotNull Long> amenityIds,
            ListingStatus listingStatus
    ) {
    }

    public record RoomCreateRequest(
            @NotBlank(message = "객실 이름을 입력해 주세요.") @Size(max = 100) String name,
            @NotNull @Min(value = 1, message = "기준 인원은 1명 이상이어야 합니다.") Integer capacity,
            @NotNull @Min(value = 1, message = "최대 인원은 1명 이상이어야 합니다.") Integer maxCapacity,
            @NotNull @DecimalMin(value = "0", message = "추가 인원 요금은 0 이상이어야 합니다.")
            @Digits(integer = 10, fraction = 2) BigDecimal extraPersonFee,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "기본가는 0보다 커야 합니다.")
            @Digits(integer = 10, fraction = 2) BigDecimal nightlyPrice,
            @Size(max = 20, message = "이미지는 20장까지 등록할 수 있습니다.")
            List<@NotBlank @Size(max = 500) String> imageUrls
    ) {
    }

    /** 보내지 않은(null) 항목은 바꾸지 않는다. */
    public record RoomUpdateRequest(
            @Size(max = 100) @Pattern(regexp = BLANK_ONLY, message = "객실 이름을 입력해 주세요.") String name,
            @Min(value = 1, message = "기준 인원은 1명 이상이어야 합니다.") Integer capacity,
            @Min(value = 1, message = "최대 인원은 1명 이상이어야 합니다.") Integer maxCapacity,
            @DecimalMin(value = "0", message = "추가 인원 요금은 0 이상이어야 합니다.")
            @Digits(integer = 10, fraction = 2) BigDecimal extraPersonFee,
            @DecimalMin(value = "0", inclusive = false, message = "기본가는 0보다 커야 합니다.")
            @Digits(integer = 10, fraction = 2) BigDecimal nightlyPrice,
            @Size(max = 20, message = "이미지는 20장까지 등록할 수 있습니다.")
            List<@NotBlank @Size(max = 500) String> imageUrls,
            RoomStatus status
    ) {
    }

    public record RefundPolicyRequest(
            @Size(max = 5000) String policyText,
            @NotEmpty(message = "환불 규칙을 한 개 이상 입력해 주세요.") @Size(max = 20)
            List<@Valid @NotNull RefundRule> rules
    ) {
    }

    /**
     * 체크인 daysBefore일 전까지 취소하면 refundRate만큼 환불한다.
     *
     * @param refundRate 0.00(환불 없음) ~ 1.00(전액)
     */
    public record RefundRule(
            @NotNull @Min(value = 0, message = "일수는 0 이상이어야 합니다.") Integer daysBefore,
            @NotNull @DecimalMin(value = "0", message = "환불 비율은 0~1 사이여야 합니다.")
            @DecimalMax(value = "1", message = "환불 비율은 0~1 사이여야 합니다.")
            @Digits(integer = 1, fraction = 2, message = "환불 비율은 소수 둘째 자리까지 입력합니다.")
            BigDecimal refundRate
    ) {
    }

    // ---------- 응답 ----------

    public record RegionResponse(Long id, Long parentId, String name, int level) {
    }

    public record AmenityResponse(Long id, String name) {
    }

    /**
     * 목록의 한 줄.
     *
     * @param minNightlyPrice 판매 중인 객실 기본가 중 최저가. 객실이 없으면 null
     */
    public record AccommodationSummary(Long id, String name, String address, Long regionId, String regionName,
                                       String thumbnailUrl, BigDecimal minNightlyPrice) {
    }

    public record RoomResponse(Long id, String name, int capacity, int maxCapacity, BigDecimal extraPersonFee,
                               BigDecimal nightlyPrice, RoomStatus status, List<String> imageUrls) {
    }

    public record RefundPolicyResponse(Long id, String version, String policyText, List<RefundRule> rules) {
    }

    public record AccommodationDetail(
            Long id,
            String name,
            String description,
            String address,
            Long regionId,
            String regionName,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            ListingStatus listingStatus,
            List<String> imageUrls,
            List<AmenityResponse> amenities,
            List<RoomResponse> rooms,
            RefundPolicyResponse refundPolicy
    ) {
    }

    // ---------- 다른 feature용 ----------

    /**
     * 예약·재고가 쓰는 객실 정보.
     *
     * @param sellable 숙소가 LISTED이고 객실이 ACTIVE인지
     */
    public record RoomInfo(Long roomId, Long accommodationId, String accommodationName, String roomName,
                           int capacity, int maxCapacity, BigDecimal extraPersonFee, BigDecimal nightlyPrice,
                           boolean sellable) {
    }
}
