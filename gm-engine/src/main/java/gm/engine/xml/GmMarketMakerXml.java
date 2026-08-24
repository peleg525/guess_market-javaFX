package gm.engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmMarketMakerXml {

    @XmlElement(name = "event")
    private List<GmEventRefXml> event = new ArrayList<>();

    public List<GmEventRefXml> getEvent() {
        return event;
    }
}
