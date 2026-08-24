package gm.engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class GmUsersXml {

    @XmlElement(name = "GM-user")
    private List<GmUserXml> user = new ArrayList<>();

    public List<GmUserXml> getUser() {
        return user;
    }
}
