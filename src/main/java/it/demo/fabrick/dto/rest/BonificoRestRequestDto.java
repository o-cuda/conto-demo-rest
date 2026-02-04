package it.demo.fabrick.dto.rest;

import java.math.BigDecimal;
import lombok.Data;

/**
 * REST request DTO for money transfer operations
 */
@Data
public class BonificoRestRequestDto {
    private Creditor creditor;
    private String description;
    private BigDecimal amount;
    private String currency;
    private String executionDate;
    private String feeType;
    private String feeAccountId;
    private TaxRelief taxRelief;

    @Data
    public static class Creditor {
        private String name;
        private Account account;
    }

    @Data
    public static class Account {
        private String accountCode;
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
