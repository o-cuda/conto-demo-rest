package it.demo.fabrick.mapper;

import java.time.LocalDate;

import it.demo.fabrick.dto.BonificoRequestDto;
import it.demo.fabrick.dto.rest.BonificoRestRequestDto;
import it.demo.fabrick.dto.rest.TransazioniResponseDto;
import it.demo.fabrick.dto.TransactionDto;

/**
 * Utility class for DTO mapping operations.
 * Provides static methods to convert between different DTO representations.
 */
public class DtoMapper {

	/**
	 * Convert BonificoRestRequestDto to BonificoRequestDto.
	 * Performs direct field mapping without intermediate JsonObject conversion.
	 *
	 * @param restRequest the REST request DTO
	 * @return the Fabrick API request DTO
	 */
	public static BonificoRequestDto toBonificoRequestDto(BonificoRestRequestDto restRequest) {
		BonificoRequestDto request = new BonificoRequestDto();

		// Map creditor
		BonificoRequestDto.Creditor creditor = new BonificoRequestDto.Creditor();
		creditor.setName(restRequest.getCreditor().getName());
		creditor.setAccount(new BonificoRequestDto.Account());
		creditor.getAccount().setAccountCode(restRequest.getCreditor().getAccount().getAccountCode());
		creditor.getAccount().setBicCode(restRequest.getCreditor().getAccount().getBicCode());
		request.setCreditor(creditor);

		// Map basic fields
		request.setExecutionDate(LocalDate.now().toString());
		request.setUri("REMITTANCE_INFORMATION");
		request.setDescription(restRequest.getDescription());
		request.setAmount(restRequest.getAmount());
		request.setCurrency(restRequest.getCurrency());
		request.setUrgent(false);
		request.setInstant(false);
		request.setFeeType(restRequest.getFeeType());
		request.setFeeAccountId(restRequest.getFeeAccountId());

		// Always create tax relief object (cannot be null per Fabrick API requirement)
		BonificoRequestDto.TaxRelief taxRelief = new BonificoRequestDto.TaxRelief();
		taxRelief.setCondoUpgrade(false); // Default to false

		// Create empty beneficiary objects (required for serialization)
		BonificoRequestDto.NaturalPersonBeneficiary naturalPersonBeneficiary =
			new BonificoRequestDto.NaturalPersonBeneficiary();
		BonificoRequestDto.LegalPersonBeneficiary legalPersonBeneficiary =
			new BonificoRequestDto.LegalPersonBeneficiary();

		// Map tax relief values if present in request
		if (restRequest.getTaxRelief() != null) {
			taxRelief.setTaxReliefId(restRequest.getTaxRelief().getTaxReliefId());
			taxRelief.setCondoUpgrade(restRequest.getTaxRelief().getIsCondoUpgrade() != null
				? restRequest.getTaxRelief().getIsCondoUpgrade()
				: false);
			taxRelief.setCreditorFiscalCode(restRequest.getTaxRelief().getCreditorFiscalCode());
			taxRelief.setBeneficiaryType(restRequest.getTaxRelief().getBeneficiaryType());

			if (restRequest.getTaxRelief().getNaturalPersonBeneficiary() != null) {
				naturalPersonBeneficiary.setFiscalCode1(
					restRequest.getTaxRelief().getNaturalPersonBeneficiary().getFiscalCode1());
			}
		}

		taxRelief.setNaturalPersonBeneficiary(naturalPersonBeneficiary);
		taxRelief.setLegalPersonBeneficiary(legalPersonBeneficiary);
		request.setTaxRelief(taxRelief);

		return request;
	}

	/**
	 * Convert TransactionDto to TransazioniResponseDto.
	 * Extracts the transaction list for the REST API response.
	 *
	 * @param transaction the Fabrick API transaction response DTO
	 * @return the REST response DTO
	 */
	public static TransazioniResponseDto toTransazioniResponseDto(TransactionDto transaction) {
		TransazioniResponseDto responseDto = new TransazioniResponseDto();
		if (transaction != null && transaction.getPayload() != null) {
			responseDto.setList(transaction.getPayload().getList());
		}
		return responseDto;
	}

	private DtoMapper() {
		// Private constructor to prevent instantiation
	}
}
