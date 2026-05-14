package com.etl.generated.job.xmltojsonevents.source;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.List;

@XmlRootElement(name = "Events")
@XmlAccessorType(XmlAccessType.FIELD)
public class EventsXmlRoot {

    @XmlElement(name = "Event")
    private List<EventsXmlRecord> eventsXmlRecord;

    public List<EventsXmlRecord> getEventsXmlRecord() {
        return eventsXmlRecord;
    }

    public void setEventsXmlRecord(List<EventsXmlRecord> eventsXmlRecord) {
        this.eventsXmlRecord = eventsXmlRecord;
    }
}

