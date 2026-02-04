package it.demo.fabrick.dto.rest;

import java.util.List;
import it.demo.fabrick.dto.ListaTransactionDto;
import lombok.Data;

/**
 * REST response DTO for transactions list operations
 */
@Data
public class TransazioniResponseDto {
    private List<ListaTransactionDto> list;
}
