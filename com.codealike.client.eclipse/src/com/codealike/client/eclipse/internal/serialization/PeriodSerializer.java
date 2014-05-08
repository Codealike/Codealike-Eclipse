package com.codealike.client.eclipse.internal.serialization;

import java.io.IOException;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class PeriodSerializer extends JsonSerializer<Period> {

	public static final PeriodFormatter FORMATER = new PeriodFormatterBuilder().printZeroAlways().minimumPrintedDigits(2).appendHours().appendLiteral(":").
			appendMinutes().appendLiteral(":").appendSeconds().appendLiteral(".").appendMillis().toFormatter();
	
	public PeriodSerializer() {
	}
	
	@Override
	public void serialize(Period period, JsonGenerator jgen, SerializerProvider provider) throws IOException,
			JsonProcessingException {
		jgen.writeString(FORMATER.print(period.toPeriod()));
	}


}
