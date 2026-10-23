package com.aquaalert.notification;

import com.aquaalert.entity.WaterSchedule;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class MessageTemplateService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public String emailText(WaterSchedule schedule) {
        return "Water supply alert\nArea: %s\nDate: %s\nTime: %s\nPlease store water."
                .formatted(schedule.getArea().getName(), schedule.getReleaseDate(), formattedTime(schedule));
    }

    public String smsText(WaterSchedule schedule) {
        return "Municipal Corporation: Water supply will begin today at %s in %s. Please store water."
                .formatted(formattedTime(schedule), schedule.getArea().getName());
    }

    public String voiceScript(WaterSchedule schedule) {
        return "Hello. This is a notice from the Municipal Corporation. "
                + "Water supply in %s will begin today at %s. Please store water. Thank you."
                        .formatted(schedule.getArea().getName(), formattedTime(schedule));
    }

    public String pushTitle() {
        return "Water Supply Alert";
    }

    public String pushBody(WaterSchedule schedule) {
        long minutesUntil = java.time.Duration.between(
                java.time.LocalDateTime.now(), schedule.getReleaseDateTime()).toMinutes();
        if (minutesUntil <= 0) {
            return "Water supply starts now in %s.".formatted(schedule.getArea().getName());
        }
        if (minutesUntil < 60) {
            return "Water supply starts in %d minutes.".formatted(minutesUntil);
        }
        long hours = Math.round(minutesUntil / 60.0);
        return "Water supply starts in %d hour%s.".formatted(hours, hours == 1 ? "" : "s");
    }

    public String whatsAppText(WaterSchedule schedule) {
        return "*Water Supply Alert*\nArea: %s\nTime: %s"
                .formatted(schedule.getArea().getName(), formattedTime(schedule));
    }

    private String formattedTime(WaterSchedule schedule) {
        return schedule.getReleaseTime().format(TIME_FORMAT);
    }
}
