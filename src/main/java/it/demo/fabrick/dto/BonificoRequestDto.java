package it.demo.fabrick.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.vertx.core.json.JsonObject;
import lombok.Data;

@Data
public class BonificoRequestDto {

	public Creditor creditor;
	public String executionDate;
	public String uri;
	public String description;
	public BigDecimal amount;
	public String currency;
	public boolean isUrgent;
	public boolean isInstant;
	public String feeType;
	public String feeAccountId;
	public TaxRelief taxRelief;

	public BonificoRequestDto() {
		// Default constructor - use DtoMapper.toBonificoRequestDto() for construction
	}

	@Data
	public static class Account {
		public String accountCode;
		public String bicCode;
	}

	@Data
	public static class Address {
		public Object address;
		public Object city;
		public Object countryCode;
	}

	@Data
	public static class Creditor {
		public String name;
		public Account account;
		public Address address;
	}

	@Data
	public static class LegalPersonBeneficiary {
		public Object fiscalCode;
		public Object legalRepresentativeFiscalCode;
	}

	@Data
	public static class NaturalPersonBeneficiary {
		public String fiscalCode1;
		public Object fiscalCode2;
		public Object fiscalCode3;
		public Object fiscalCode4;
		public Object fiscalCode5;
	}

	@Data
	public static class TaxRelief {
		public String taxReliefId;
		public boolean isCondoUpgrade;
		public String creditorFiscalCode;
		public String beneficiaryType;
		public NaturalPersonBeneficiary naturalPersonBeneficiary;
		public LegalPersonBeneficiary legalPersonBeneficiary;
	}


}
