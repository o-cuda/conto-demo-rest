package it.demo.fabrick.dto.rest;

import lombok.Data;

/**
 * REST response DTO for balance operations
 */
@Data
public class SaldoResponseDto {
    private double balance;
    private double availableBalance;
    private String currency;

    public static SaldoResponseDto fromBalanceDto(it.demo.fabrick.dto.BalanceDto.Payload payload) {
        SaldoResponseDto response = new SaldoResponseDto();
        response.setBalance(payload.getBalance());
        response.setAvailableBalance(payload.getAvailableBalance());
        response.setCurrency(payload.getCurrency());
        return response;
    }
}
