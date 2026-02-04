package it.demo.fabrick.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class TransactionDto {

	public String status;
	public List<Error> error;
	public Payload payload;

	@Data
	@NoArgsConstructor
	public class Payload {
		public List<ListaTransactionDto> list;
	}

	@Data
	public class Error{
	    public String code;
	    public String description;
	    public String params;
	}

}
