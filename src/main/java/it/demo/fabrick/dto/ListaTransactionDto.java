package it.demo.fabrick.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ListaTransactionDto {

	public String transactionId;
	public String operationId;
	public String accountingDate;
	public String valueDate;
	public Type type;
	public BigDecimal amount;
	public String currency;
	public String description;

	@Data
	public class Type {
		public String enumeration;
		public String value;
	}

}
