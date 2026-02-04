package it.demo.fabrick.dto;

import lombok.Data;

@Data
public class ListaTransactionDto {

	public String transactionId;
	public String operationId;
	public String accountingDate;
	public String valueDate;
	public Type type;
	public double amount;
	public String currency;
	public String description;

	@Data
	public class Type {
		public String enumeration;
		public String value;
	}

}
