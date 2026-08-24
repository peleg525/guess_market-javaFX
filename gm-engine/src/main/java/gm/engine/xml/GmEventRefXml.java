package gm.engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmEventRefXml {

    @XmlAttribute(name = "id")
    private Integer id;

    public Integer getId() {
        return id;
    }
}
