package it.demo.fabrick.dto.rest;

import lombok.Data;

/**
 * REST response DTO for money transfer operations
 */
@Data
public class BonificoRestResponseDto {
    private String status;
    private String message;
    private String transferId;

    public static BonificoRestResponseDto success(String transferId) {
        BonificoRestResponseDto response = new BonificoRestResponseDto();
        response.setStatus("OK");
        response.setMessage("Transfer executed successfully");
        response.setTransferId(transferId);
        return response;
    }

    public static BonificoRestResponseDto error(String message) {
        BonificoRestResponseDto response = new BonificoRestResponseDto();
        response.setStatus("ERROR");
        response.setMessage(message);
        return response;
    }
}
