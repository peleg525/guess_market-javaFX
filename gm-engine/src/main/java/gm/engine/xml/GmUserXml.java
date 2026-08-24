package gm.engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmUserXml {

    @XmlAttribute(name = "name")
    private String name;

    @XmlElement(name = "initial-cash")
    private Integer initialCash;

    @XmlElement(name = "GM-market-maker")
    private GmMarketMakerXml marketMaker;

    public String getName() {
        return name;
    }

    public Integer getInitialCash() {
        return initialCash;
    }

    public GmMarketMakerXml getMarketMaker() {
        return marketMaker;
    }
}
