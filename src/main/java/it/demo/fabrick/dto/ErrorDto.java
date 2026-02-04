package it.demo.fabrick.dto;

import java.util.ArrayList;

import lombok.Data;

@Data
public class ErrorDto {

	public String status;
	public ArrayList<AnErrorDto> errors;
	public Object payload;

}
