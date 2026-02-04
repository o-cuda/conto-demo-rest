package it.demo.fabrick.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class BalanceDto {

	public String status;
	public List<Error> error;
	public Payload payload;
	
	@Data
	public class Payload{
	    public String date;
	    public BigDecimal balance;
	    public BigDecimal availableBalance;
	    public String currency;
	}

	@Data
	public class Error{
	    public String code;
	    public String description;
	    public String params;
	}

}
