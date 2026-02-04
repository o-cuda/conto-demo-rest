package it.demo.fabrick.dto;

import java.util.ArrayList;

import lombok.Data;

@Data
public class BalanceDto {

	public String status;
	public ArrayList<Error> error;
	public Payload payload;
	
	@Data
	public class Payload{
	    public String date;
	    public double balance;
	    public double availableBalance;
	    public String currency;
	}

	@Data
	public class Error{
	    public String code;
	    public String description;
	    public String params;
	}

}
