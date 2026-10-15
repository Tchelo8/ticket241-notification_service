package com.ticket241.notification_service.service;

import com.ticket241.notification_service.dto.request.OtpRequestDto;
import com.ticket241.notification_service.dto.request.OtpVerificationRequestDto;
import com.ticket241.notification_service.dto.response.OtpResponseDto;
import com.ticket241.notification_service.dto.response.OtpVerificationResponseDto;
import com.ticket241.notification_service.entity.OtpCode;
import com.ticket241.notification_service.exception.NotificationException;
import com.ticket241.notification_service.repository.OtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final Long USER_ID = 42L;

    @Mock
    private OtpCodeRepository otpCodeRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(otpCodeRepository, emailNotificationService);
        ReflectionTestUtils.setField(otpService, "otpExpiryMinutes", 5);
        ReflectionTestUtils.setField(otpService, "otpLength", 6);
        ReflectionTestUtils.setField(otpService, "otpHashSecret", "test-pepper");
    }

    // Crée un code actif dont le hash correspond à "123456", via generateOtp pour réutiliser le vrai hachage
    private OtpCode activeCodeFor(String clearCode) {
        String hash = ReflectionTestUtils.invokeMethod(otpService, "hashOtp", clearCode);
        return OtpCode.builder()
                .id(1L)
                .userId(USER_ID)
                .email("user@test.com")
                .code(hash)
                .isUsed(false)
                .isExpired(false)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    private OtpVerificationResponseDto verifyCode(String code) {
        return otpService.verifyOtp(new OtpVerificationRequestDto(USER_ID, code));
    }

    @Test
    void wrongCodeCountsAsAttemptAndReportsRemaining() {
        OtpCode active = activeCodeFor("123456");
        when(otpCodeRepository.findFirstByUserIdAndIsUsedFalseAndIsExpiredFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(active));

        OtpVerificationResponseDto response = verifyCode("000000");

        assertThat(response.getValid()).isFalse();
        assertThat(response.getRemainingAttempts()).isEqualTo(4);
        assertThat(active.getAttemptCount()).isEqualTo(1);
        assertThat(active.getIsExpired()).isFalse();
    }

    @Test
    void fifthWrongCodeBurnsTheCode() {
        OtpCode active = activeCodeFor("123456");
        when(otpCodeRepository.findFirstByUserIdAndIsUsedFalseAndIsExpiredFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(active));

        for (int i = 0; i < 4; i++) {
            assertThat(verifyCode("000000").getValid()).isFalse();
        }
        OtpVerificationResponseDto fifth = verifyCode("000000");

        assertThat(fifth.getValid()).isFalse();
        assertThat(fifth.getRemainingAttempts()).isZero();
        assertThat(active.getIsExpired()).isTrue();
    }

    @Test
    void correctCodeAfterAWrongOneIsAccepted() {
        OtpCode active = activeCodeFor("123456");
        when(otpCodeRepository.findFirstByUserIdAndIsUsedFalseAndIsExpiredFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(active));

        verifyCode("000000");
        OtpVerificationResponseDto response = verifyCode("123456");

        assertThat(response.getValid()).isTrue();
        assertThat(active.getIsUsed()).isTrue();
    }

    @Test
    void expiredCodeIsRejectedEvenIfCorrect() {
        OtpCode active = activeCodeFor("123456");
        active.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(otpCodeRepository.findFirstByUserIdAndIsUsedFalseAndIsExpiredFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(active));

        assertThat(verifyCode("123456").getValid()).isFalse();
        assertThat(active.getIsExpired()).isTrue();
    }

    @Test
    void noActiveCodeIsRejected() {
        when(otpCodeRepository.findFirstByUserIdAndIsUsedFalseAndIsExpiredFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.empty());

        assertThat(verifyCode("123456").getValid()).isFalse();
    }

    @Test
    void generateAndSendOtpEmailsTheCodeAndNeverReturnsIt() {
        when(otpCodeRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        OtpRequestDto request = new OtpRequestDto(USER_ID, "user@test.com", "Awa");

        OtpResponseDto response = otpService.generateAndSendOtp(request);

        ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
        verify(emailNotificationService).sendOtpEmail(eq("user@test.com"), eq("Awa"), sentCode.capture(), eq(USER_ID));
        assertThat(sentCode.getValue()).matches("\\d{6}");
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getOtpCode()).isNull();
    }

    @Test
    void generateAndSendOtpPropagatesEmailFailure() {
        when(otpCodeRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        doThrow(new NotificationException("SMTP down"))
                .when(emailNotificationService).sendOtpEmail(anyString(), any(), anyString(), anyLong());

        assertThatThrownBy(() -> otpService.generateAndSendOtp(new OtpRequestDto(USER_ID, "user@test.com", "Awa")))
                .isInstanceOf(NotificationException.class);
    }
}
