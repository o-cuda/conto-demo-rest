package it.demo.fabrick.dto.rest;

import java.math.BigDecimal;
import lombok.Data;

/**
 * REST response DTO for balance operations
 */
@Data
public class SaldoResponseDto {
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;

    public static SaldoResponseDto fromBalanceDto(it.demo.fabrick.dto.BalanceDto.Payload payload) {
        SaldoResponseDto response = new SaldoResponseDto();
        response.setBalance(payload.getBalance());
        response.setAvailableBalance(payload.getAvailableBalance());
        response.setCurrency(payload.getCurrency());
        return response;
    }
}
