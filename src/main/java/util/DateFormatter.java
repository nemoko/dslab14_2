package util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Quelle: http://stackoverflow.com/questions/18589986/date-conversion-with-threadlocal
 */
public class DateFormatter {

    static ThreadLocal<SimpleDateFormat> format1 = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMdd_HHmmss.SSS");
        }
    };

    public String formatDate(Date date) {
        return format1.get().format(date);
    }
}