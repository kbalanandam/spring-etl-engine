package com.etl.generated.job.xmltojsonevents.source;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Event")
@XmlAccessorType(XmlAccessType.FIELD)
public class EventsXmlRecord {

    @XmlElement(name = "eventCode")
    private String eventCode;

    @XmlElement(name = "eventTime")
    private String eventTime;

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }
}

