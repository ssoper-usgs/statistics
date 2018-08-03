package gov.usgs.ngwmn.logic;

import static gov.usgs.wma.statistics.model.Value.*;
import static  org.apache.commons.lang.StringUtils.*;

//import java.io.IOException;
//import java.io.Reader;
//import java.sql.SQLException;
//import javax.xml.parsers.ParserConfigurationException;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.xml.sax.SAXException;
//import java.text.SimpleDateFormat;
//import java.util.Calendar;
//import java.text.ParseException;
//import java.math.RoundingMode;
//import java.util.Collections;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.usgs.ngwmn.model.DepthDatum;
import gov.usgs.ngwmn.model.PCode;
import gov.usgs.ngwmn.model.Specifier;
import gov.usgs.ngwmn.model.WLSample;
import gov.usgs.wma.statistics.logic.StatisticsCalculator;
import gov.usgs.wma.statistics.model.Value;

public class WaterLevelStatistics extends StatisticsCalculator<WLSample> {
	
	/**
	 * This is the agreed upon days window for a recent value. It is computed from
	 * 1 year + 1 month + 1.5 weeks or 365 + 30 + 7 + 4 because of how samples are taken and eventually entered.
	 */
	protected static final BigDecimal Days406 = new BigDecimal("406");
	public static final String MONTHLY_WARNING =  "Too few data values for monthly statistics."
			+ " Ten years required with no gaps and most recent value within " + Days406 + " days.";

	private final transient Logger logger = LoggerFactory.getLogger(getClass());
	
	public static enum MediationType {
		BelowLand, 
		AboveDatum;
	}
	
	// TODO business rule for any one value error? continue without value or error entire statistics calculation?

	@Override
	public String calculate(Specifier spec, List<WLSample> samples) {
		logger.info("Executing WaterLevel Stats calculations.");
		
		MediationType mediation = findMostPrevalentMediation(spec, samples);
		List<WLSample> samplesByDate = useMostPrevalentPCodeMediatedValue(spec, samples, mediation); 
		
		removeProvisionalButNotMostRecent(samplesByDate, spec.getAgencyCd()+":"+spec.getSiteNo());
		removeNulls(samplesByDate, spec.getAgencyCd()+":"+spec.getSiteNo());
		
		List<WLSample> sortedByValue  = new ArrayList<>(samplesByDate);
		sortByMediation(sortedByValue, mediation);
		
		Map<String, String> overall = overallStats(samplesByDate, sortedByValue, mediation);
		Map<String, Map<String, String>> monthly = null; // Could use empty collection rather than null?
		
		if ( isNotBlank( overall.get(RECORD_YEARS) ) ) {
			BigDecimal years = new BigDecimal( overall.get(RECORD_YEARS) );
			String recent = overall.get(MAX_DATE);
			String today = today();
			
			try {
				if ( doesThisSiteQualifyForMonthlyStats(years, recent, today) ) {
					overall.put(IS_RANKED, "Y");
					monthly = monthlyStats(sortedByValue, mediation);
				}
			} catch (Exception e) {
				// if anything goes wrong here we still want the overall
				// TODO should we log it?
			}
		} else {
			logger.warn("Record Years is null for {}:{}, by passing monthly stats.", spec.getAgencyCd(), spec.getSiteNo());
		}
		Map<String, Object> stats = new HashMap<>();
		stats.put("overall", overall);
		
		if (monthly == null) {
			stats.put("monthly", MONTHLY_WARNING);
		} else {
			stats.put("monthly", monthly);
		}
		
		String json = "";
		try {
			json = new ObjectMapper().writeValueAsString(stats);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.err.println(json);
		
		return json;
	}
	

	/**
	 * Helper method that checks all USGS site PCODEs for the most prevalent mediation.
	 * @param spec site specification because only USGS site have PCODEs and the default is below surface
	 * @param samples the samples to potentially change mediation type
	 * @return Above or Below mediation type based on sample PCODE counts
	 */
	protected MediationType findMostPrevalentMediation(Specifier spec, List<WLSample> samples) {
		if ( ! "USGS".equals(spec.getAgencyCd())) {
			return MediationType.BelowLand;
		}
		int countBelowLand  = 0;
		int countAboveDatum = 0;
		int half = samples.size()/2+1; // add one for rounding up simply
		
		for (WLSample sample : samples) {
			PCode pcode = PCode.get(sample.pcode);
			
			if (pcode.isUnrecognized() || pcode.isUnspecified() // default is below
					// and there are below datum
					|| DepthDatum.BLS.equals(pcode.getDatum()) 
					|| DepthDatum.LAND_SURFACE.equals(pcode.getDatum()) ) {
				countBelowLand++;
			} else { // otherwise we are above
				countAboveDatum++;
			}
			// we need not look any more if one is more than half
			if (countBelowLand > half || countAboveDatum > half) {
				break;
			}
		}
		if (countAboveDatum > countBelowLand) {
			return MediationType.AboveDatum;
		}
		
		return MediationType.BelowLand; // default
	}
	/**
	 * Helper method to change out the value (below surface) to valueAboveDatum if above datum mediation is most prevalent.
	 * Otherwise, it will return the default which is below surface mediated.
	 * 
	 * @param spec site specification because only USGS site have PCODEs and the default is below surface
	 * @param samples the samples to potentially change mediation type
	 * @param mediation the determined majority PCODE mediation direction.
	 * @return the original list if below surface mediation and a new sample list if above datum mediation
	 */
	protected List<WLSample> useMostPrevalentPCodeMediatedValue(Specifier spec, List<WLSample> samples, MediationType mediation) {
		if (! "USGS".equals(spec.getAgencyCd())) {
			return samples;
		}
		// default value will be depth below land surface as given from WaterLevelDAO.extractSamples()
		// all samples are pre-mediated in both directions and the value is depth below land surface
		// the will determine if value should be changed to the height above a datum (like sea floor)
		// all values will use the same mediation of the most prevalent.
		List<WLSample> mostPrevalent = samples;
		
		if (mediation == MediationType.AboveDatum) {
			mostPrevalent = new ArrayList<>(samples.size());
			for (WLSample sample : samples) {
				mostPrevalent.add(new WLSample(sample.time, sample.valueAboveDatum, sample.units, sample.originalValue, sample.comment, sample.up, sample.pcode, sample.valueAboveDatum));
			}
		}
		
		return mostPrevalent;
	}
	
	
	/**
	 * This removes provisional value samples from a collection of samples.
	 * However, it is required that the most recent sample be retained regardless of status
	 * @param samples the samples to examine in temporal order
	 * @param mySiteId for logging purposes if there are nulls removed to ID the site with nulls
	 */
	protected void removeProvisionalButNotMostRecent(List<WLSample> samples, String mySiteId) {
		// retain most recent sample
		WLSample latestSample = samples.get(samples.size()-1);
		
		super.removeProvisional(samples, mySiteId);
		
		// but only add it back in if it is provisional
		if (latestSample.isProvisional()) {
			samples.add(latestSample);
		}
	}


	/**
	 * This convenience method is suppost to be self documenting by its name. But just to be clear it
	 * ensures at least ten years of data are required and the most recent value must be within the past year
	 * 
	 * Not that this method could have computed all thes values by a given data set of overall data. However,
	 * it is easier to test the conditions if the values are given. For example, if today was determined inside
	 * then the test could not use a given date for today.
	 * 
	 * @param years  the years of data
	 * @param recent the date of the most recent value
	 * @param today  today's date in the same UTC format
	 * @return
	 */
	protected boolean doesThisSiteQualifyForMonthlyStats(BigDecimal years, String recent, String today) {
		return BigDecimal.TEN.compareTo(years) <= 0 && Days406.compareTo(daysDiff(today, recent)) >= 0;
	}


	/**
	 * @param sortedByValue a list of all site samples in sorted order by value
	 * @return a map of monthly maps of percentile data
	 */
	protected Map<String,Map<String,String>> monthlyStats(List<WLSample> sortedByValue, MediationType mediation) {
		Map<String,Map<String,String>> stats = new HashMap<>();
		if (sortedByValue == null || sortedByValue.size() == 0) {
			return stats;
		}
		
		for(int m=1; m<=12; m++) {
			String month = ""+m;
			logger.debug("MONTH="+m);
			logger.debug("SBVC="+sortedByValue.size());
			List<WLSample> monthSamples = filterValuesByGivenMonth(sortedByValue, month);
			logger.debug("MSC="+monthSamples.size());
			String monthCount = ""+monthSamples.size();
			Map<String, List<WLSample>> sortSamplesByYear = sortSamplesByYear(monthSamples);
			List<WLSample> normalizeMutlipleYearlyValues = normalizeMutlipleYearlyValues(monthSamples, mediation);
			logger.debug("NMYVC="+normalizeMutlipleYearlyValues.size());
			
			if ( doesThisMonthQualifyForStats(normalizeMutlipleYearlyValues) ) {
				Map<String,String> monthStats = generatePercentiles(normalizeMutlipleYearlyValues, PERCENTILES);
				stats.put(month, monthStats);
				
				List<WLSample> monthYearlyMedians = generateMonthYearlyPercentiles(normalizeMutlipleYearlyValues, mediation);
				monthStats.put(P50_MIN, monthYearlyMedians.get(0).value.toString());
				monthStats.put(P50_MAX, monthYearlyMedians.get( monthYearlyMedians.size()-1 ).value.toString());
				monthStats.put(SAMPLE_COUNT, monthCount);

				int recordYears = sortSamplesByYear.keySet().size();
				monthStats.put(RECORD_YEARS, ""+recordYears);
			}
		}
		
		return stats;
	}

	public List<WLSample> normalizeMutlipleYearlyValues(List<WLSample> monthSamples, MediationType mediation) {
		List<WLSample> normalizedSamples = super.normalizeMutlipleYearlyValues(monthSamples, sortByMediation(mediation));
		return normalizedSamples;
	}
	
	protected boolean doesThisMonthQualifyForStats(List<WLSample> monthSamples) {
		if (monthSamples.size()<10) {
			return false;
		}
		return uniqueYears(monthSamples)>=10;
	}
	
	/**
	 * @param samples a many year single month filtered sample list in value order
	 * @return a list of new samples holding the yearly medians in value order
	 */
	protected List<WLSample> generateMonthYearlyPercentiles(List<WLSample> samples, MediationType mediation) {
		samples = new ArrayList<>(samples);
		List<WLSample> monthYearlyMedians = new LinkedList<>(); 

		while (samples.size() > 0) {
			// calculate a year's median
			final String year = yearUTC(samples.get(0).time);
			// using predicate because spring 3.x includes cglib that cannot compile lambdas
			Predicate<WLSample> yearly = new Predicate<WLSample>() {
				@Override
				public boolean test(WLSample sample) {
					return year.equals( yearUTC(sample.time) );
				}
			};
			List<WLSample> yearSamples = samples.stream().filter(yearly).collect(Collectors.toList());
			monthYearlyMedians.add( new WLSample(valueOfPercentile( yearSamples, PERCENTILES.get(P50), WLSample::valueOf)) );
			// remove the current year
			samples.removeAll(yearSamples);
		}
		
		sortByMediation(monthYearlyMedians, mediation);
		return monthYearlyMedians;
	}

	protected WLSample makeMedian(List<WLSample> samples) {
		// years median in the this month
		BigDecimal medianValue = valueOfPercentile(samples, StatisticsCalculator.PERCENTILES.get(StatisticsCalculator.P50), Value::valueOf);
		BigDecimal medianAbove = valueOfPercentile(samples, StatisticsCalculator.PERCENTILES.get(StatisticsCalculator.P50), WLSample::valueOfAboveDatum);
		WLSample base = samples.get( (int)(samples.size()/2) );
		WLSample medianSample = new WLSample(medianValue, medianAbove, base);
		return medianSample;
	}

	
	/**
	 * This calculates the overall series statistics: min, max, count, first, last, years, and percentile of the most recent
	 * @param samples time series data in temporal order
	 * @param sortedByValue  time series data in value order
	 * @return computed statistics map
	 */
	protected Map<String,String> overallStats(List<WLSample> samples, List<WLSample> sortedByValue, MediationType mediation) {
		if (samples == null || samples.size() == 0) {
			return new HashMap<String,String>();
		}
		
		Map<String,String> stats = findMinMaxDatesAndDateRange(samples,sortedByValue); // after this samples is sorted by date for certain
		
		WLSample minValue = sortedByValue.get(0);
		WLSample maxValue = sortedByValue.get(sortedByValue.size()-1);

		stats.put(IS_RANKED, "N"); // default not ranked status

		// value range
		stats.put(MIN_VALUE, minValue.value.toString());
		stats.put(MAX_VALUE, maxValue.value.toString());
		// number of samples
		stats.put(SAMPLE_COUNT, ""+samples.size());
		// percentile statistics
		stats.put(MEDIAN, valueOfPercentile(sortedByValue, PERCENTILES.get(P50), WLSample::valueOf).toString());
		
		String latestPercentile = generateLatestPercentileBasedOnMonthlyData(samples, stats.get(MAX_DATE), mediation);
		stats.put(LATEST_PCTILE, latestPercentile);
		
		stats.put(MEDIATION, mediation.toString());
		stats.put(CALC_DATE, DATE_FORMAT_FULL.format(new Date()));
		
		return stats;
	}
	
	protected String generateLatestPercentileBasedOnMonthlyData(List<WLSample> samplesByDate, String maxDate, MediationType mediation) {
		String month = monthUTC(maxDate);
		List<WLSample> monthlySamples = filterValuesByGivenMonth(samplesByDate, month);
		WLSample latestSample = monthlySamples.get( monthlySamples.size()-1 );
		sortByMediation(monthlySamples, mediation);
		BigDecimal percentile = percentileOfValue(monthlySamples, latestSample, WLSample::valueOf);
		return percentile.toString();
	}

	protected Map<String,String> findMinMaxDatesAndDateRange(List<WLSample> samples, List<WLSample> sortedByValue) {
		Map<String,String> stats = new HashMap<String,String>();
		sortByDateOrder(samples); // ensure date order for sample sourced from db or tests
		WLSample minDate =samples.get(0);
		WLSample maxDate =samples.get(samples.size()-1);
		removeMostRecentProvisional(samples, sortedByValue);
		
		stats.put(LATEST_VALUE, maxDate.value.toString());
		// date range
		stats.put(MIN_DATE, minDate.time);
		stats.put(MAX_DATE, maxDate.time);
		// years of data
		stats.put(RECORD_YEARS, ""+yearDiff(maxDate.time, minDate.time) );
		
		return stats;
	}
	
	protected void removeMostRecentProvisional(List<WLSample> samples, List<WLSample> sortedByValue) {
		WLSample maxDate =samples.get(samples.size()-1);
		
		if (maxDate.isProvisional()) {
			samples.remove(maxDate);
			sortedByValue.remove(maxDate);
		}
	}
	protected void sortByMediation(List<WLSample> sortedByValue, MediationType mediation) {
		if (mediation == MediationType.BelowLand) {
			sortByValueOrderDescending(sortedByValue);
		} else {
			sortByValueOrderAscending(sortedByValue);
		}
	}
	protected Function<List<WLSample>, List<WLSample>> sortByMediation(MediationType mediation) {
		Function<List<WLSample>, List<WLSample>> sortBy = StatisticsCalculator::sortByValueOrderAscending;
		
		if (mediation == MediationType.BelowLand) {
			sortBy = StatisticsCalculator::sortByValueOrderDescending;
		}
		return sortBy;
	}
	
}
