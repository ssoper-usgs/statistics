package gov.usgs.wma.statistics.control;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.usgs.ngwmn.logic.WaterLevelStatistics;
import gov.usgs.ngwmn.logic.WaterLevelStatistics.MediationType;
import gov.usgs.ngwmn.model.Specifier;
import gov.usgs.ngwmn.model.WLSample;
import gov.usgs.wma.statistics.app.SwaggerConfig;
import gov.usgs.wma.statistics.model.JsonData;
import gov.usgs.wma.statistics.model.JsonDataBuilder;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController
@RequestMapping("/statistics")
@CrossOrigin(origins = "*") // no credentials by default
//@Api("StatisticsService")
public class StatsService {
	private static Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StatsService.class);

	private static final ResponseEntity<String> _404_ = new ResponseEntity<String>(HttpStatus.NOT_FOUND);
	
	
//	@TimeSeriesData
	@ApiOperation(
			value = "Calculate Statistics Service",
			notes = SwaggerConfig.StatsService_CALCULATE_NOTES
		)
	@PostMapping(value = "/calculate",
			produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
			consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
		)
	public ResponseEntity<String> calculate(
			@ApiParam(
					value  = SwaggerConfig.StatsService_CALCULATE_DATA,
					example= SwaggerConfig.StatsService_EXAMPLE_RAW,
					required = true
				)
			@RequestParam
			String data,
			@ApiParam(
					value="mediation",
					defaultValue="NONE",
					required=false,
					allowMultiple=false,
					allowableValues="AboveDatum,BelowLand,NONE,ASCENDING,DESCENDING",
					allowEmptyValue=true
					)
			@RequestParam(defaultValue="NONE")
			String mediate) {
		
		try {
			List<WLSample> samples = parseData(data);
			MediationType mediation = MediationType.valueOf(mediate);
			JsonDataBuilder builder = new JsonDataBuilder();
			builder.mediation(mediation);
			
			// A random identifier for the service unless we parameterize the date set ID.
			Specifier spec = new Specifier();
			
			JsonData json = new WaterLevelStatistics(builder).calculate(spec, samples);
			
			return ResponseEntity.ok( toJSON(json) );
		} catch (Exception e) {
			return ResponseEntity.ok("{'status':400,'message':'"+e.getMessage()+"'");
		}
	}
	
//	@TimeSeriesData
	@ApiOperation(
			value = "Statistics Medians Service",
			notes = SwaggerConfig.StatsService_MEDIANS_NOTES
		)
	@PostMapping(
			value = "/calculate/medians",
			produces = MediaType.APPLICATION_JSON_UTF8_VALUE
		)
	public ResponseEntity<String> medians(
			@ApiParam(
					value  = SwaggerConfig.StatsService_CALCULATE_DATA,
					example= SwaggerConfig.StatsService_EXAMPLE_RAW,
					required = true
				)
			@RequestParam 
			String data,
			@ApiParam(
					value="mediation",
					defaultValue="NONE",
					required=false,
					allowMultiple=false,
					allowableValues="AboveDatum,BelowLand,NONE,ASCENDING,DESCENDING",
					allowEmptyValue=true
					)
			@RequestParam(defaultValue="NONE")
			String mediate) {
		
		try {
			List<WLSample> samples = parseData(data);
			MediationType mediation = MediationType.valueOf(mediate);
			JsonDataBuilder builder = new JsonDataBuilder();
			builder.mediation(mediation);
			builder.setIncludeIntermediateValues(true);
			
			// A random identifier for the service unless we parameterize the date set ID.
			Specifier spec = new Specifier();
			
			JsonData json = new WaterLevelStatistics(builder).calculate(spec, samples);
			
			return ResponseEntity.ok( toJSON(json) );
		} catch (Exception e) {
			return ResponseEntity.ok("{'status':400,'message':'"+e.getMessage()+"'");
		}
	}

	public static String toJSON(JsonData stats) {
		String json = "";
		try {
			json = new ObjectMapper().writeValueAsString(stats);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return json;
	}

	
	public List<WLSample> parseData(String data){
		String[] rows = data.split("\r?\n");
		return parseData(rows);
	}
	public List<WLSample> parseData(String[] data) {
		
		List<WLSample> samples = new ArrayList<>(data.length);
		
		for (String row : data) {
			row = row.trim();
			if ( 0 == row.length() || row.charAt(0) == '#') {
				continue; // skip empty and comment rows
			}
			
			String[] cols = row.split(",");
			if (cols.length == 0) {
				continue; // skip empty rows
			}
			
			if (cols.length < 2 || cols.length > 3) {
				throw new RuntimeException("All rows must have two values and optional provisional code: date,value,P. ");
			}
			
			String time = cols[0].trim();
			if ( time.length() < 10 ) {
				throw new RuntimeException("The date must be valid (yyyy-mm-dd). " + time);
			}
			
			try {
				BigDecimal value = new BigDecimal(cols[1].trim());
				
				WLSample sample = new WLSample(time, value, "ft", value, "", true, "", value);
				
				if (cols.length == 3) {
					sample.setProvsional(true);
				}
				samples.add(sample);
			} catch (NumberFormatException e) {
				throw new RuntimeException("The water value must be valid. " + cols[1]);
			}
		}
		
		return samples;
	}

}