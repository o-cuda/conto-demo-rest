package it.demo.fabrick.dto;

import java.util.ArrayList;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class TransactionDto {

	public String status;
	public ArrayList<Error> error;
	public Payload payload;

	@Data
	@NoArgsConstructor
	public class Payload {
		public ArrayList<ListaTransactionDto> list;
	}

	@Data
	public class Error{
	    public String code;
	    public String description;
	    public String params;
	}

}
