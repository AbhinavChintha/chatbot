package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class CurrentDateTimeService {

    private static final ZoneId HYDERABAD_ZONE =
            ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "EEEE, dd MMMM yyyy",
                    Locale.ENGLISH
            );

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "hh:mm:ss a z",
                    Locale.ENGLISH
            );

    /**
     * Handles current date/time questions using Java's clock.
     *
     * Returns null when the message is not a recognized
     * current date/time question.
     */
    public ChatResponse handleIfApplicable(String message) {

        if (message == null || message.isBlank()) {
            return null;
        }

        String normalized =
                message.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9\\s']", " ")
                        .replaceAll("\\s+", " ")
                        .trim();

        boolean asksDate = isCurrentDateQuestion(normalized);
        boolean asksTime = isCurrentTimeQuestion(normalized);

        if (!asksDate && !asksTime) {
            return null;
        }

        StringBuilder answer = new StringBuilder();

        if (asksDate) {

            LocalDate today =
                    LocalDate.now(HYDERABAD_ZONE);

            answer.append("Today's date in Hyderabad is ")
                    .append(today.format(DATE_FORMATTER))
                    .append(".");
        }

        if (asksTime) {

            ZonedDateTime now =
                    ZonedDateTime.now(HYDERABAD_ZONE);

            if (!answer.isEmpty()) {
                answer.append("\n");
            }

            answer.append("The current time in Hyderabad is ")
                    .append(now.format(TIME_FORMATTER))
                    .append(" (IST, UTC+05:30).");
        }

        return new ChatResponse(
                "### Answer\n\n" + answer,
                "Java Date/Time API",
                "GENERAL",
                true,
                null
        );
    }

    private boolean isCurrentDateQuestion(String message) {

        boolean mentionsDate =
                message.contains("date")
                        || message.contains("day");

        boolean mentionsCurrentDay =
                message.contains("today")
                        || message.contains("current date")
                        || message.contains("current day")
                        || message.contains("right now");

        boolean asksWhatDate =
                message.matches(
                        ".*\\b(what|whats|tell|show|give)\\b.*\\b(date|day)\\b.*"
                );

        return (mentionsDate && mentionsCurrentDay)
                || (asksWhatDate && mentionsCurrentDay)
                || message.matches(
                ".*\\bwhat day is it\\b.*"
        );
    }

    private boolean isCurrentTimeQuestion(String message) {

        boolean mentionsTime =
                message.matches(".*\\btime\\b.*");

        if (!mentionsTime) {
            return false;
        }

        boolean asksCurrentTime =
                message.contains("now")
                        || message.contains("current")
                        || message.contains("right now")
                        || message.contains("today");

        boolean asksWhatTime =
                message.matches(
                        ".*\\b(what|whats|tell|show|give)\\b.*\\btime\\b.*"
                );

        boolean mentionsHyderabadOrIndia =
                message.contains("hyderabad")
                        || message.contains("india")
                        || message.matches(".*\\bist\\b.*");

        return asksCurrentTime
                || asksWhatTime
                || mentionsHyderabadOrIndia;
    }
}