package com.staybook.inventory.dto;

import com.staybook.inventory.domain.RoomInventory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** inventory 요청·응답 모음. */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    /**
     * 기간별 수량, 가격, 판매 마감 설정. startDate부터 endDate까지(양 끝 포함) 같은 값으로 설정한다.
     * 보내지 않은(null) 항목은 바꾸지 않는다. 재고가 없던 날짜는 새로 만든다.
     */
    public record SetInventoriesRequest(
            @NotNull(message = "시작일을 입력해 주세요.") LocalDate startDate,
            @NotNull(message = "종료일을 입력해 주세요.") LocalDate endDate,
            @Min(value = 0, message = "수량은 0 이상이어야 합니다.")
            @Max(value = 9999, message = "수량이 너무 큽니다.") Integer totalCount,
            @DecimalMin(value = "0", inclusive = false, message = "가격은 0보다 커야 합니다.")
            @Digits(integer = 10, fraction = 2) BigDecimal price,
            Boolean closed
    ) {
    }

    public record InventoryResponse(LocalDate stayDate, int totalCount, int bookedCount, int heldCount,
                                    BigDecimal price, boolean closed) {

        public static InventoryResponse from(RoomInventory inventory) {
            return new InventoryResponse(inventory.getStayDate(), inventory.getTotalCount(),
                    inventory.getBookedCount(), inventory.getHeldCount(), inventory.getPrice(),
                    inventory.isClosed());
        }
    }

    /**
     * 객실별 예약 가능 여부와 기간 합계 요금.
     *
     * @param available  숙박일 전체에 재고가 있고 판매 중인지
     * @param totalPrice 숙박일별 판매가의 합(기준 인원 기준). 재고가 없는 날짜가 있으면 null
     */
    public record RoomAvailability(Long roomId, String roomName, int capacity, int maxCapacity,
                                   BigDecimal extraPersonFee, int nights, boolean available,
                                   BigDecimal totalPrice) {
    }

    /** 선점 결과의 하루치. 예약이 요금 항목을 계산할 때 쓴다. */
    public record NightPrice(LocalDate stayDate, BigDecimal price) {
    }
}
