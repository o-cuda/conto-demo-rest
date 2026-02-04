package it.demo.fabrick.dto.rest;

import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * REST request DTO for money transfer operations
 */
@Data
public class BonificoRestRequestDto {
    @Valid
    @NotNull(message = "Creditor is required")
    private Creditor creditor;

    @NotBlank(message = "Description is required")
    @Pattern(regexp = "^.{1,140}$", message = "Description must be between 1 and 140 characters")
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid ISO 4217 code (3 uppercase letters)")
    private String currency;

    private String feeType;
    private String feeAccountId;
    private TaxRelief taxRelief;

    @Data
    public static class Creditor {
        @NotBlank(message = "Creditor name is required")
        @Pattern(regexp = "^.{1,70}$", message = "Creditor name must be between 1 and 70 characters")
        private String name;

        @Valid
        @NotNull(message = "Creditor account is required")
        private Account account;
    }

    @Data
    public static class Account {
        @NotBlank(message = "Account code (IBAN) is required")
        @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$", message = "Account code must be a valid IBAN")
        private String accountCode;

        @Pattern(regexp = "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$", message = "BIC code must be a valid SWIFT/BIC code")
        private String bicCode;
    }

    @Data
    public static class TaxRelief {
        private String taxReliefId;
        private Boolean isCondoUpgrade;
        private String creditorFiscalCode;
        private String beneficiaryType;
        private NaturalPersonBeneficiary naturalPersonBeneficiary;
    }

    @Data
    public static class NaturalPersonBeneficiary {
        private String fiscalCode1;
    }
}
