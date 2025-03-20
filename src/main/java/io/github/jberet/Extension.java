package io.github.jberet;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class Extension {
    public static String formatDate(String text, String format) {
        LocalDate date = LocalDate.parse(text);
        return DateTimeFormatter.ofPattern(format, Locale.ENGLISH).format(date);
    }

}
