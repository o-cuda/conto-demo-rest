package it.demo.fabrick.dto;

import io.vertx.core.json.JsonArray;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfigurazioneDto {

	private String operation = null;
	private String ambiente = null;
	private String messageIn = null;
	private String messageOutFromBus = null;
	private String indirizzo = null;
	
	public ConfigurazioneDto(JsonArray line) {

		this.setOperation(line.getString(0));
		this.setAmbiente(line.getString(1));
		this.setMessageIn(line.getString(2));
		this.setMessageOutFromBus(line.getString(3));
		this.setIndirizzo(line.getString(4));
	}

	public JsonArray toJsonArray() {

		JsonArray json = new JsonArray();

		json.add(operation);
		json.add(ambiente);
		json.add(messageIn);
		json.add(messageOutFromBus);
		json.add(indirizzo);

		return json;
	}

}
