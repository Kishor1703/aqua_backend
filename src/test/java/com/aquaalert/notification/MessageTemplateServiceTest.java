package com.aquaalert.notification;

import com.aquaalert.entity.Area;
import com.aquaalert.entity.WaterSchedule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplateServiceTest {

    private final MessageTemplateService templates = new MessageTemplateService();

    private WaterSchedule sampleSchedule() {
        Area area = Area.builder().id(1L).name("Gandhi Nagar").ward("Ward 5").zone("North Zone").build();
        return WaterSchedule.builder()
                .area(area)
                .releaseDate(LocalDate.now())
                .releaseTime(LocalTime.of(18, 0))
                .notifyBeforeMins(60)
                .build();
    }

    @Test
    void smsText_includesAreaAndTime() {
        String text = templates.smsText(sampleSchedule());
        assertThat(text).contains("Gandhi Nagar").contains("6:00 PM").contains("Please store water");
    }

    @Test
    void voiceScript_includesAreaAndTime() {
        String script = templates.voiceScript(sampleSchedule());
        assertThat(script).startsWith("Hello.").contains("Gandhi Nagar").contains("6:00 PM");
    }

    @Test
    void whatsAppText_includesAreaAndTime() {
        String text = templates.whatsAppText(sampleSchedule());
        assertThat(text).contains("Water Supply Alert").contains("Gandhi Nagar").contains("6:00 PM");
    }

    @Test
    void pushTitle_isConsistent() {
        assertThat(templates.pushTitle()).isEqualTo("Water Supply Alert");
    }
}
