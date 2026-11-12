package com.staybook.payment.service;

import com.staybook.common.crypto.HashUtils;
import com.staybook.common.exception.BaseException;
import com.staybook.common.exception.ErrorCode;
import com.staybook.payment.domain.Payment;
import com.staybook.payment.domain.PaymentLog;
import com.staybook.payment.domain.Refund;
import com.staybook.payment.dto.PaymentDtos.ConfirmRequest;
import com.staybook.payment.dto.PaymentDtos.PaymentResponse;
import com.staybook.payment.dto.PaymentDtos.PrepareRequest;
import com.staybook.payment.dto.PaymentDtos.PrepareResponse;
import com.staybook.payment.exception.PaymentErrorCode;
import com.staybook.payment.pg.PgClient;
import com.staybook.payment.pg.PgClient.Approval;
import com.staybook.payment.pg.PgClient.Cancellation;
import com.staybook.payment.pg.PgClient.WebhookEvent;
import com.staybook.payment.pg.PgClient.WebhookStatus;
import com.staybook.payment.repository.PaymentLogRepository;
import com.staybook.payment.repository.PaymentRepository;
import com.staybook.payment.repository.RefundRepository;
import com.staybook.reservation.dto.ReservationDtos.PaymentInfo;
import com.staybook.reservation.service.ReservationCancelledEvent;
import com.staybook.reservation.service.ReservationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 준비·승인·웹훅·환불.
 * <p>
 * 예약과의 연결은 두 가지뿐이다.
 * 결제 성공 시 reservationService.confirm(reservationId)를 직접 호출하고,
 * 예약 취소는 ReservationCancelledEvent를 커밋 이후에 받아 환불한다.
 * <p>
 * 승인 실패나 만료 후 결제처럼 "상태는 저장하고 요청은 실패로 응답"해야 하는 경우가 있어,
 * 트랜잭션 안에서는 결과만 만들고 커밋한 뒤에 예외를 던진다.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String ORDER_ID_PREFIX = "ORD-";

    private final PaymentRepository paymentRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final RefundRepository refundRepository;
    private final ReservationService reservationService;
    private final PgClient pgClient;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate newTransactionTemplate;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, PaymentLogRepository paymentLogRepository,
                          RefundRepository refundRepository, ReservationService reservationService,
                          PgClient pgClient, PlatformTransactionManager transactionManager, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentLogRepository = paymentLogRepository;
        this.refundRepository = refundRepository;
        this.reservationService = reservationService;
        this.pgClient = pgClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // ---------- 준비 ----------

    /** 결제 준비. order_id를 발급하고 READY로 저장한다. 아직 READY인 결제가 있으면 그것을 다시 준다. */
    @Transactional
    public PrepareResponse prepare(Long userId, PrepareRequest request) {
        PaymentInfo reservation = reservationService.getPaymentInfo(request.reservationId());
        if (!reservation.userId().equals(userId)) {
            throw new BaseException(PaymentErrorCode.NOT_OWNER);
        }
        if (!reservation.payable()) {
            throw new BaseException(PaymentErrorCode.RESERVATION_NOT_PAYABLE);
        }
        Payment payment = paymentRepository
                .findFirstByReservationIdAndStatusOrderByIdDesc(reservation.reservationId(), Payment.Status.READY)
                .orElseGet(() -> paymentRepository.save(Payment.ready(reservation.reservationId(),
                        ORDER_ID_PREFIX + HashUtils.randomToken(24), pgClient.provider(), reservation.totalPrice(),
                        now())));
        return new PrepareResponse(payment.getOrderId(), payment.getAmount(), reservation.title(),
                payment.getPgProvider());
    }

    // ---------- 승인 ----------

    /**
     * PG 승인 요청. 성공하면 PAID로 바꾸고 예약을 확정한다.
     * 같은 order_id로 다시 요청이 와도 PG 승인은 한 번만 하고 같은 결과를 돌려준다.
     */
    public PaymentResponse confirm(Long userId, ConfirmRequest request) {
        Outcome outcome = transactionTemplate.execute(tx -> doConfirm(userId, request));
        if (outcome.error() != null) {
            throw new BaseException(outcome.error());
        }
        return outcome.response();
    }

    private Outcome doConfirm(Long userId, ConfirmRequest request) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId())
                .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        PaymentInfo reservation = reservationService.getPaymentInfo(payment.getReservationId());
        if (!reservation.userId().equals(userId)) {
            throw new BaseException(PaymentErrorCode.NOT_OWNER);
        }
        if (payment.isStatus(Payment.Status.PAID)) {
            return Outcome.ok(payment);   // 중복 요청: 이미 처리한 결과를 그대로 돌려준다
        }
        if (!payment.isStatus(Payment.Status.READY)) {
            throw new BaseException(PaymentErrorCode.ALREADY_PROCESSED);
        }
        // 승인 전에 요청 금액이 예약의 total_price와 같은지 서버에서 검증한다
        if (request.amount().compareTo(reservation.totalPrice()) != 0
                || payment.getAmount().compareTo(reservation.totalPrice()) != 0) {
            throw new BaseException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        Approval approval = pgClient.approve(payment.getOrderId(), request.paymentKey(), payment.getAmount());
        saveLog(payment, PaymentLog.EVENT_APPROVE, null, approval.responseCode());
        if (!approval.success()) {
            payment.markFailed();
            return Outcome.failed(payment, PaymentErrorCode.PG_APPROVAL_FAILED);
        }
        return completePaid(payment, approval.paymentKey(), approval.method());
    }

    /** PAID로 바꾸고 예약을 확정한다. 예약이 이미 만료로 취소됐다면 즉시 전액 환불한다. */
    private Outcome completePaid(Payment payment, String paymentKey, String method) {
        payment.markPaid(paymentKey, method, now());
        if (reservationService.confirm(payment.getReservationId())) {
            return Outcome.ok(payment);
        }
        refund(payment, payment.getAmount(), BigDecimal.ZERO, "결제 제한 시간 경과 후 결제 승인");
        return Outcome.failed(payment, PaymentErrorCode.RESERVATION_EXPIRED_REFUNDED);
    }

    // ---------- 웹훅 ----------

    /** PG 웹훅 수신. 서명을 검증하고, 이미 처리한 이벤트(provider_event_id)는 무시한다. */
    public void handleWebhook(String rawBody, Map<String, String> headers) {
        if (!pgClient.verifyWebhook(rawBody, headers)) {
            throw new BaseException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
        WebhookEvent event = pgClient.parseWebhook(rawBody, headers);
        if (event == null) {
            throw new BaseException(PaymentErrorCode.INVALID_WEBHOOK_PAYLOAD);
        }
        transactionTemplate.executeWithoutResult(tx -> {
            Optional<Payment> found = paymentRepository.findByOrderIdForUpdate(event.orderId());
            if (found.isEmpty()) {
                log.warn("알 수 없는 주문의 웹훅을 무시합니다. eventType={}", event.eventType());
                return;
            }
            Payment payment = found.get();
            // 결제 행을 잠근 상태라 같은 이벤트가 동시에 와도 한쪽만 통과한다
            if (paymentLogRepository.existsByProviderEventId(event.eventId())) {
                return;
            }
            saveLog(payment, event.eventType(), event.eventId(), event.responseCode());

            if (event.status() == WebhookStatus.PAID && payment.isStatus(Payment.Status.READY)
                    && event.paymentKey() != null) {
                completePaid(payment, event.paymentKey(), event.method());
            } else if (event.status() == WebhookStatus.FAILED && payment.isStatus(Payment.Status.READY)) {
                payment.markFailed();
            }
        });
    }

    // ---------- 환불 ----------

    /**
     * 예약 취소 이벤트 처리. 예약 취소가 커밋된 뒤에 별도 트랜잭션으로 실행된다.
     * 환불액 = 결제 금액 × 환불 비율(원 단위 미만 버림), 나머지는 위약금.
     * PG 취소가 실패하면 환불 상태를 FAILED로 남기고 예약 취소는 되돌리지 않는다.
     */
    public void refundForCancellation(ReservationCancelledEvent event) {
        newTransactionTemplate.executeWithoutResult(tx -> paymentRepository
                .findByReservationIdAndStatusForUpdate(event.reservationId(), Payment.Status.PAID)
                .forEach(payment -> {
                    BigDecimal refundAmount = payment.getAmount().multiply(event.refundRate())
                            .setScale(0, RoundingMode.DOWN);
                    refund(payment, refundAmount, payment.getAmount().subtract(refundAmount), event.reason());
                }));
    }

    private void refund(Payment payment, BigDecimal amount, BigDecimal penaltyAmount, String reason) {
        Refund refund = refundRepository.save(Refund.requested(payment.getId(), amount, penaltyAmount, reason));
        if (amount.signum() == 0) {
            // 환불액이 없으면 PG를 호출하지 않는다 (전액 위약금)
            refund.complete(now());
            return;
        }
        Cancellation cancellation = pgClient.cancel(payment.getPaymentKey(), amount, reason);
        saveLog(payment, PaymentLog.EVENT_CANCEL, null, cancellation.responseCode());
        if (cancellation.success()) {
            refund.complete(now());
            payment.markRefunded();
        } else {
            refund.fail();
            log.error("환불 실패: paymentId={}, responseCode={}", payment.getId(), cancellation.responseCode());
        }
    }

    // ---------- 내부 ----------

    private void saveLog(Payment payment, String eventType, String providerEventId, String responseCode) {
        paymentLogRepository.save(PaymentLog.of(payment.getId(), eventType, providerEventId, responseCode, now()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 트랜잭션 안에서 만든 결과. error가 있으면 커밋한 뒤 그 에러로 응답한다. */
    private record Outcome(PaymentResponse response, ErrorCode error) {

        static Outcome ok(Payment payment) {
            return new Outcome(PaymentResponse.from(payment), null);
        }

        static Outcome failed(Payment payment, ErrorCode error) {
            return new Outcome(PaymentResponse.from(payment), error);
        }
    }
}
