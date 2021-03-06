package com.codealike.client.eclipse.internal.serialization;

import java.io.IOException;

import org.joda.time.DateTime;

import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class DateTimeSerializer extends JsonSerializer<DateTime> {

	
	@Override
	public void serialize(DateTime dateTime, JsonGenerator jgen, SerializerProvider provider) throws IOException,
			JsonProcessingException {
//		jgen.writeString(String.format("/Date(%d)/", dateTime.getMillis()));
		String formattedDate = PluginContext.getInstance().getDateTimeFormatter().print(dateTime);
		jgen.writeString(formattedDate);
	}

}
