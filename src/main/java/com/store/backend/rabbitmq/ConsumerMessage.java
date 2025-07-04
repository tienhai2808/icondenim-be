package com.store.backend.rabbitmq;

import java.util.Optional;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.store.backend.config.RabbitMQConfig;
import com.store.backend.order.dto.OrderEmailDto;
import com.store.backend.order.entity.OrderEntity;
import com.store.backend.order.repository.OrderRepository;
import com.store.backend.smtp.EmailService;
import com.store.backend.user.dto.AuthEmailDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConsumerMessage {
  private final EmailService emailService;
  private final OrderRepository orderRepository;

  @RabbitListener(queues = RabbitMQConfig.AUTH_QUEUE)
  public void consumeAuthEmail(AuthEmailDto message) {
    log.info("Nhận message xác thực: {}", message);
    emailService.sendAuthEmail(message.getTo(), message.getSubject(), message.getOtp());
    log.info("Đã gửi email");
  }

  @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
  public void consumeOrderEmail(OrderEmailDto message) {
    log.info("Nhận được message từ đơn hàng: {}", message.getOrderId());
    Optional<OrderEntity> optionalOrder = orderRepository.findById(message.getOrderId());
    if (optionalOrder.isEmpty()) {
      log.error("Không tìm thấy đơn hàng: {}", message.getOrderId());
      return;
    }
    OrderEntity order = optionalOrder.get();
    log.info("Bắt đầu gửi email đơn hàng tới {}", message.getTo());
    emailService.sendOrderEmail(message.getTo(), message.getSubject(), order, message.getConfirmLink());
    log.info("Đã gửi mail");
  }
}
