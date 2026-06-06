package com.etl.common.util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class TimeZoneTest {

    public static void main(String[] args) {

        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
        String requiredPattern = "yyyyMMdd";

        String utcTimestamp = "2026-06-01 21:30:00";

        System.out.println("Input String : " + utcTimestamp);
        System.out.println("JVM TZ       : " + TimeZone.getDefault().getID());
        System.out.println("Current Output   : " + getDateUsingSimpleDateFormatwithZone(utcTimestamp, requiredPattern));
        System.out.println("After fix Output   : " + getUTCDateStringByUTCTimestampString(utcTimestamp, requiredPattern));
    }
  //Old method
    static String getDateUsingSimpleDateFormatwithZone(String utcString, String requiredPattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(requiredPattern);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf.setLenient(false);
        return sdf.format(Timestamp.valueOf(utcString));
    }
// new code
    static String getUTCDateStringByUTCTimestampString(String utcTimestamp, String requiredPattern) {
        Timestamp ts = Timestamp.valueOf(utcTimestamp);
        LocalDateTime ldt = ts.toLocalDateTime();
        return ldt.format(DateTimeFormatter.ofPattern(requiredPattern));
    }
}