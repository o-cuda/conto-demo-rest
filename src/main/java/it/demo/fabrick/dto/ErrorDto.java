package it.demo.fabrick.dto;

import java.util.List;

import lombok.Data;

@Data
public class ErrorDto {

	public String status;
	public List<AnErrorDto> errors;
	public Object payload;

}
