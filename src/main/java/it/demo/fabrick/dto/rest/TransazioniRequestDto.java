package it.demo.fabrick.dto.rest;

import lombok.Data;

/**
 * REST request DTO for transactions list operations
 */
@Data
public class TransazioniRequestDto {
    private String accountId;
    private String fromDate;
    private String toDate;
}
