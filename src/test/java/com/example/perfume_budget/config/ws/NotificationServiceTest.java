package com.example.perfume_budget.config.ws;

import com.example.perfume_budget.enums.CurrencyCode;
import com.example.perfume_budget.enums.UserRole;
import com.example.perfume_budget.model.Money;
import com.example.perfume_budget.model.Notification;
import com.example.perfume_budget.model.NotificationRecipient;
import com.example.perfume_budget.model.Order;
import com.example.perfume_budget.model.Payment;
import com.example.perfume_budget.model.User;
import com.example.perfume_budget.repository.NotificationRecipientRepository;
import com.example.perfume_budget.repository.NotificationRepository;
import com.example.perfume_budget.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private AdminSessionManager adminSessionManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationRecipientRepository notificationRecipientRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Payment payment;
    private User adminOne;
    private User adminTwo;

    @BeforeEach
    void setUp() {
        adminOne = User.builder()
                .id(1L)
                .email("admin1@example.com")
                .roles(UserRole.ADMIN)
                .build();

        adminTwo = User.builder()
                .id(2L)
                .email("admin2@example.com")
                .roles(UserRole.ADMIN)
                .build();

        Order order = Order.builder()
                .orderNumber("ORD-123")
                .totalAmount(new Money(new BigDecimal("100.00"), CurrencyCode.GHS))
                .build();

        payment = Payment.builder()
                .order(order)
                .customerEmail("customer@example.com")
                .paidAt(LocalDateTime.of(2026, 4, 4, 12, 0))
                .build();
    }

    @Test
    void notifyAdminsOfSuccessfulPayment_PersistsForAllAdmins_WhenNoOneIsConnected() {
        Notification savedNotification = Notification.builder()
                .id(10L)
                .type("PAYMENT_SUCCESS")
                .title("Payment received")
                .message("Order #ORD-123 has been successfully paid.")
                .referenceType("ORDER")
                .referenceId("ORD-123")
                .createdAt(LocalDateTime.of(2026, 4, 4, 12, 0))
                .build();

        when(userRepository.findAllByRolesIn(List.of(UserRole.ADMIN, UserRole.FRONT_DESK))).thenReturn(List.of(adminOne, adminTwo));
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        when(notificationRecipientRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(adminSessionManager.getConnectedAdmins()).thenReturn(Set.of());

        notificationService.notifyAdminsOfSuccessfulPayment(payment);

        ArgumentCaptor<List<NotificationRecipient>> recipientCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRecipientRepository).saveAll(recipientCaptor.capture());
        List<NotificationRecipient> savedRecipients = recipientCaptor.getValue();
        assertEquals(2, savedRecipients.size());
        assertEquals(adminOne, savedRecipients.getFirst().getUser());
        assertEquals(adminTwo, savedRecipients.get(1).getUser());
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void notifyAdminsOfSuccessfulPayment_SendsRealtimeOnlyToConnectedAdmins() {
        Notification savedNotification = Notification.builder()
                .id(10L)
                .type("PAYMENT_SUCCESS")
                .title("Payment received")
                .message("Order #ORD-123 has been successfully paid.")
                .referenceType("ORDER")
                .referenceId("ORD-123")
                .createdAt(LocalDateTime.of(2026, 4, 4, 12, 0))
                .build();

        NotificationRecipient recipientOne = NotificationRecipient.builder()
                .id(100L)
                .notification(savedNotification)
                .user(adminOne)
                .isRead(false)
                .build();

        NotificationRecipient recipientTwo = NotificationRecipient.builder()
                .id(101L)
                .notification(savedNotification)
                .user(adminTwo)
                .isRead(false)
                .build();

        when(userRepository.findAllByRolesIn(List.of(UserRole.ADMIN, UserRole.FRONT_DESK))).thenReturn(List.of(adminOne, adminTwo));
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        when(notificationRecipientRepository.saveAll(any()))
                .thenReturn(List.of(recipientOne, recipientTwo))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminSessionManager.getConnectedAdmins()).thenReturn(Set.of("admin1@example.com"));

        notificationService.notifyAdminsOfSuccessfulPayment(payment);

        assertNotNull(recipientOne.getDeliveredAt());
        assertEquals(null, recipientTwo.getDeliveredAt());
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("admin1@example.com"), eq("/queue/notifications"), any());
    }
}
