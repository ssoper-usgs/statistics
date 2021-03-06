package gov.usgs.ngwmn.model;

import java.math.BigDecimal;

import org.apache.commons.lang.StringUtils;

public class Elevation {
	public final BigDecimal value;
	public final String datum;
	
	public Elevation() {
		this(null,null);
	}
	
	public Elevation(BigDecimal value, String datum) {
		this.value = value;
		this.datum = StringUtils.trimToNull(datum);
	}
	
	public boolean isValid() {
		return (value != null && datum != null && !"NA".equalsIgnoreCase(datum));
	}

	@Override
	public String toString() {
		return "Elevation [value=" + value + ", datum=" + datum + "]";
	}
}
