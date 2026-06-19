package com.flashsale.orderservice.axon.saga;

import com.flashsale.orderservice.axon.event.*;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Saga
@Slf4j
public class ParentOrderPaymentSaga {

    @Autowired
    private transient OrderRepository orderRepository;

    @Autowired
    private transient EventGateway eventGateway;

    @Autowired
    private transient ParentOrderRepository parentOrderRepository;

    @StartSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    public void on(ParentOrderCheckoutCreatedEvent event) {
        // Payment request is published by OrderCheckoutService after the creating
        // transaction commits (publishPaymentRequestedAfterCommit → direct Kafka send).
        // This saga only tracks the payment lifecycle — it handles payment.succeeded
        // and payment.failed events below.
        //
        // We DO NOT publish payment.requested here because the saga runs inside the
        // creating transaction (EventGateway is synchronous), so
        // findAllByParentOrderIdAndStatus would query the DB before the sub-orders
        // are committed — returning 0 rows and creating a Stripe PI with no orders.
        // The afterCommit hook in OrderCheckoutService avoids this race by building
        // the orders array from the in-memory list and sending after the DB commit.
        log.info("[ParentPaymentSaga][{}] Saga started, awaiting payment result", event.getParentOrderId());
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    @Transactional
    public void on(ParentOrderPaymentSucceededEvent event) {
        // Pessimistic lock prevents concurrent transactions from incrementing ParentOrder.version
        // while we process sub-orders. Previously this used findById (no lock), which caused
        // ObjectOptimisticLockingFailureException when another transaction committed first.
        parentOrderRepository.findByIdWithPessimisticLock(event.getParentOrderId());

        List<Order> orders = orderRepository.findAllByParentOrderIdAndStatusWithLock(event.getParentOrderId(), "PENDING");
        for (Order order : orders) {
            order.setStatus("PAID");
            orderRepository.save(order);
            eventGateway.publish(new OrderPaidEvent(
                    order.getId(),
                    order.getParentOrderId(),
                    order.getCustomerId(),
                    order.getSellerId(),
                    order.getFinalAmt()
            ));
        }

        log.info("[ParentPaymentSaga][{}] Payment succeeded, updated {} sub-orders", event.getParentOrderId(), orders.size());
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    @Transactional
    public void on(ParentOrderPaymentFailedEvent event) {
        String failReason = event.getReason() == null || event.getReason().isBlank()
                ? "Thanh toan that bai"
                : event.getReason();

        // Pessimistic lock prevents concurrent transactions from incrementing ParentOrder.version
        // while we process sub-orders. Previously this used findById (no lock), which caused
        // ObjectOptimisticLockingFailureException when another transaction committed first.
        parentOrderRepository.findByIdWithPessimisticLock(event.getParentOrderId());

        List<Order> orders = orderRepository.findAllByParentOrderIdAndStatusWithLock(event.getParentOrderId(), "PENDING");
        for (Order order : orders) {
            order.setStatus("CANCELLED");
            order.setCancelledBy("SYSTEM");
            order.setCancelReason(failReason);
            orderRepository.save(order);

            eventGateway.publish(new OrderCancelledEvent(
                    order.getId(),
                    order.getParentOrderId(),
                    order.getCustomerId(),
                    order.getSellerId(),
                    "PAYMENT_FAILED",
                    failReason,
                    order.getTotalAmt()
            ));
        }

        log.info("[ParentPaymentSaga][{}] Payment failed, cancelled {} sub-orders", event.getParentOrderId(), orders.size());
    }
}

