
package com.kycautomate.customer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.kycautomate.customer.dto.KycApplicationResponse;
import com.kycautomate.customer.entity.Customer;
import com.kycautomate.customer.entity.KycApplication;
import com.kycautomate.customer.enums.ApplicationStatus;
import com.kycautomate.customer.exception.BusinessConflictException;
import com.kycautomate.customer.exception.ResourceNotFoundException;
import com.kycautomate.customer.repository.KycApplicationRepository;
import com.kycautomate.customer.repository.KycStatusOperationRepository;

@ExtendWith(MockitoExtension.class)
class KycApplicationServiceTest {

    @Mock
    private KycApplicationRepository applicationRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private DocumentValidationService documentValidationService;

    @Mock
    private ApplicationStatusValidator statusValidator;

    @InjectMocks
    private KycApplicationService service;
    
    @Mock
    private KycStatusOperationRepository operationRepository;

    private Customer customer;

    @BeforeEach
    void setup() {
        customer = new Customer();

        ReflectionTestUtils.setField(
            customer, "customerId", 10L);

        customer.setFullName("Test Customer");
        customer.setDateOfBirth(LocalDate.of(2000, 1, 1));
        customer.setAddress("Test Address");
        customer.setCity("Pune");
        customer.setState("Maharashtra");
        customer.setPostalCode("411001");
    }

    @Test
    void shouldCreateDraftApplication() {

        when(customerService.getCustomerEntity(anyString()))
            .thenReturn(customer);

        when(applicationRepository
            .existsByCustomerCustomerIdAndApplicationStatusIn(
                eq(10L), anyList()))
            .thenReturn(false);

        when(applicationRepository.saveAndFlush(any()))
            .thenAnswer(invocation -> {
                KycApplication application =
                    invocation.getArgument(0);

                ReflectionTestUtils.setField(
                    application, "applicationId", 101L);

                return application;
            });

        KycApplicationResponse response =
            service.createApplication("test@example.com");

        assertEquals(ApplicationStatus.DRAFT,
            response.applicationStatus());

        assertEquals("Pune", response.applicantCity());

        assertEquals("411001",
            response.applicantPostalCode());

        assertNotNull(response.applicationRef());
    }

    @Test
    void shouldRejectDuplicateActiveApplication() {

        when(customerService.getCustomerEntity(anyString()))
            .thenReturn(customer);

        when(applicationRepository
            .existsByCustomerCustomerIdAndApplicationStatusIn(
                eq(10L), anyList()))
            .thenReturn(true);

        assertThrows(
            BusinessConflictException.class,
            () -> service.createApplication("test@example.com"));

        verify(applicationRepository, never())
            .saveAndFlush(any());
    }

    @Test
    void shouldSubmitWhenDocumentsAreComplete() {

        KycApplication application = createDraft();

        when(customerService.getCustomerEntity(anyString()))
            .thenReturn(customer);

        when(applicationRepository
            .findByApplicationIdAndCustomerCustomerId(101L, 10L))
            .thenReturn(Optional.of(application));

        when(applicationRepository.saveAndFlush(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        KycApplicationResponse response =
            service.submitApplication("test@example.com", 101L);

        assertEquals(ApplicationStatus.SUBMITTED,
            response.applicationStatus());

        assertNotNull(response.submittedAt());

        verify(documentValidationService)
            .validateDocuments(101L);
    }

    @Test
    void shouldNotSubmitWhenDocumentsAreMissing() {

        KycApplication application = createDraft();

        when(customerService.getCustomerEntity(anyString()))
            .thenReturn(customer);

        when(applicationRepository
            .findByApplicationIdAndCustomerCustomerId(101L, 10L))
            .thenReturn(Optional.of(application));

        doThrow(new BusinessConflictException(
            "Missing documents"))
            .when(documentValidationService)
            .validateDocuments(101L);

        assertThrows(
            BusinessConflictException.class,
            () -> service.submitApplication(
                "test@example.com", 101L));

        assertEquals(ApplicationStatus.DRAFT,
            application.getApplicationStatus());

        verify(applicationRepository, never())
            .saveAndFlush(any());
    }

    @Test
    void shouldNotAccessAnotherCustomersApplication() {

        when(customerService.getCustomerEntity(anyString()))
            .thenReturn(customer);

        when(applicationRepository
            .findByApplicationIdAndCustomerCustomerId(999L, 10L))
            .thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> service.getApplication(
                "test@example.com", 999L));
    }

    private KycApplication createDraft() {

        KycApplication application = new KycApplication();

        ReflectionTestUtils.setField(
            application, "applicationId", 101L);

        application.setCustomer(customer);
        application.setApplicationRef("KYC-TEST-101");
        application.setApplicantName("Test Customer");
        application.setApplicantDob(LocalDate.of(2000, 1, 1));
        application.setApplicantAddress("Test Address");
        application.setApplicantCity("Pune");
        application.setApplicantState("Maharashtra");
        application.setApplicantPostalCode("411001");
        application.setApplicationStatus(ApplicationStatus.DRAFT);

        return application;
    }
}
