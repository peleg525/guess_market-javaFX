package gm.engine.xml;

import gm.engine.exception.GmFileException;
import gm.engine.model.CommissionType;
import gm.engine.model.Event;
import gm.engine.model.LmsrEvent;
import gm.engine.model.LoadedData;
import gm.engine.model.OrderBookEvent;
import gm.engine.model.User;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads and validates a Guess Market XML file (Exercise 2 format), turning it into ready-to-use
 * {@link Event}/{@link User} objects. Validation happens in three stages, and every problem found
 * at a given stage is reported together rather than stopping at the first one:
 * <ol>
 *     <li>path / file-existence / .xml extension</li>
 *     <li>XSD schema validity</li>
 *     <li>application-level rules that the schema cannot express</li>
 * </ol>
 */
public class GmFileLoader {

    private static final String SCHEMA_RESOURCE = "/gm/engine/xml/GM-EX2-Schema.xsd";
    private static final int MIN_COMMISSION = 0;
    private static final int MAX_COMMISSION = 90;
    private static final int REQUIRED_OPTION_COUNT = 2;

    public LoadedData load(String rawPath) {
        File file = validatePathAndExtension(rawPath);
        GuessMarketXml root = parseWithSchemaValidation(file);

        List<GmEventXml> xmlEvents = (root.getEvents() == null) ? List.of() : root.getEvents().getEvent();
        List<GmUserXml> xmlUsers = (root.getUsers() == null) ? List.of() : root.getUsers().getUser();

        List<String> problems = new ArrayList<>();
        validateEvents(xmlEvents, problems);
        validateUsers(xmlUsers, xmlEvents, problems);
        if (!problems.isEmpty()) {
            throw new GmFileException(problems);
        }

        List<Event> events = buildEvents(xmlEvents, xmlUsers);
        List<User> users = buildUsers(xmlUsers);
        return new LoadedData(events, users);
    }

    private File validatePathAndExtension(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new GmFileException("No file path was entered.");
        }
        String path = rawPath.trim();
        if (!path.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new GmFileException("The file path must end with a \".xml\" extension: \"" + path + "\"");
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new GmFileException("No file was found at path: \"" + path + "\"");
        }
        return file;
    }

    private GuessMarketXml parseWithSchemaValidation(File file) {
        List<String> schemaProblems = new ArrayList<>();
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema;
            try (InputStream xsdStream = GmFileLoader.class.getResourceAsStream(SCHEMA_RESOURCE)) {
                schema = schemaFactory.newSchema(new StreamSource(xsdStream));
            }
            Validator validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    // ignored: warnings do not make the file invalid
                }

                @Override
                public void error(SAXParseException exception) {
                    schemaProblems.add(describe(exception));
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    schemaProblems.add(describe(exception));
                }
            });
            validator.validate(new StreamSource(file));
        } catch (SAXException | IOException e) {
            schemaProblems.add("The file could not be parsed as XML: " + e.getMessage());
        }

        if (!schemaProblems.isEmpty()) {
            throw new GmFileException(schemaProblems);
        }

        try {
            JAXBContext context = JAXBContext.newInstance(GuessMarketXml.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (GuessMarketXml) unmarshaller.unmarshal(file);
        } catch (JAXBException e) {
            throw new GmFileException("The file could not be read: " + e.getMessage());
        }
    }

    private String describe(SAXParseException e) {
        return "Line " + e.getLineNumber() + ": " + e.getMessage();
    }

    private void validateEvents(List<GmEventXml> events, List<String> problems) {
        Map<Integer, List<String>> eventLabelsById = new LinkedHashMap<>();

        for (GmEventXml event : events) {
            String label = describeEvent(event);

            if (event.getId() != null) {
                eventLabelsById.computeIfAbsent(event.getId(), id -> new ArrayList<>()).add(label);
            }

            Integer commission = event.getCommission() != null ? event.getCommission().getValue() : null;
            if (commission != null && (commission < MIN_COMMISSION || commission > MAX_COMMISSION)) {
                problems.add("Event " + label + " has an invalid commission of " + commission
                        + "%. Commission must be between " + MIN_COMMISSION + " and " + MAX_COMMISSION + " (inclusive).");
            }

            int optionCount = (event.getOptions() != null) ? event.getOptions().getOption().size() : 0;
            if (optionCount != REQUIRED_OPTION_COUNT) {
                problems.add("Event " + label + " must have exactly two options, but has " + optionCount + ".");
            }

            GmMethodXml method = event.getMethod();
            if (method != null && method.getLmsr() != null) {
                Integer b = method.getLmsr().getB();
                if (b != null && b <= 0) {
                    problems.add("Event " + label + " has a non-positive liquidity value (b=" + b
                            + "). b must be a positive integer.");
                }
            } else if (method != null && method.getOrderBook() != null) {
                GmOrderBookXml ob = method.getOrderBook();
                if (ob.getD() != null && ob.getD() <= 0) {
                    problems.add("Event " + label + " has a non-positive base value (d=" + ob.getD()
                            + "). d must be a positive integer.");
                }
                if (ob.getInitial() != null && ob.getInitial() < 0) {
                    problems.add("Event " + label + " has a negative initial investment (" + ob.getInitial() + ").");
                }
            }
        }

        for (Map.Entry<Integer, List<String>> entry : eventLabelsById.entrySet()) {
            if (entry.getValue().size() > 1) {
                problems.add("Event id " + entry.getKey() + " is used by more than one event: "
                        + String.join(", ", entry.getValue()) + ". Each event must have a unique id.");
            }
        }
    }

    private void validateUsers(List<GmUserXml> users, List<GmEventXml> events, List<String> problems) {
        Set<Integer> knownEventIds = events.stream()
                .map(GmEventXml::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<String, Integer> nameCounts = new HashMap<>();
        Map<Integer, List<String>> marketMakersByEventId = new LinkedHashMap<>();

        for (GmUserXml user : users) {
            String name = user.getName() == null ? "(no name)" : user.getName();
            nameCounts.merge(name, 1, Integer::sum);

            if (user.getInitialCash() != null && user.getInitialCash() <= 0) {
                problems.add("User '" + name + "' has an initial cash balance of " + user.getInitialCash()
                        + ". It must be greater than 0.");
            }

            if (user.getMarketMaker() != null) {
                for (GmEventRefXml ref : user.getMarketMaker().getEvent()) {
                    if (ref.getId() == null) {
                        continue;
                    }
                    if (!knownEventIds.contains(ref.getId())) {
                        problems.add("User '" + name + "' is listed as the market maker of event id "
                                + ref.getId() + ", but no such event exists in this file.");
                    } else {
                        marketMakersByEventId.computeIfAbsent(ref.getId(), id -> new ArrayList<>()).add(name);
                    }
                }
            }
        }

        nameCounts.forEach((name, count) -> {
            if (count > 1) {
                problems.add("User name '" + name + "' is used by " + count + " users. User names must be unique.");
            }
        });

        for (Integer eventId : knownEventIds) {
            List<String> mms = marketMakersByEventId.getOrDefault(eventId, List.of());
            if (mms.isEmpty()) {
                problems.add("Event id " + eventId + " has no market maker assigned to it. Every event must have exactly one.");
            } else if (mms.size() > 1) {
                problems.add("Event id " + eventId + " has more than one market maker assigned to it: "
                        + String.join(", ", mms) + ". Every event must have exactly one.");
            }
        }
    }

    private String describeEvent(GmEventXml event) {
        String idPart = (event.getId() != null) ? "#" + event.getId() : "with no id";
        String namePart = (event.getName() != null) ? "'" + event.getName() + "'" : "(no name)";
        return namePart + " (" + idPart + ")";
    }

    private List<Event> buildEvents(List<GmEventXml> xmlEvents, List<GmUserXml> xmlUsers) {
        Map<Integer, String> marketMakerByEventId = new HashMap<>();
        for (GmUserXml user : xmlUsers) {
            if (user.getMarketMaker() == null) {
                continue;
            }
            for (GmEventRefXml ref : user.getMarketMaker().getEvent()) {
                marketMakerByEventId.put(ref.getId(), user.getName().trim());
            }
        }

        List<Event> result = new ArrayList<>();
        for (GmEventXml x : xmlEvents) {
            List<String> options = x.getOptions().getOption().stream()
                    .map(GmOptionXml::getValue)
                    .map(String::trim)
                    .collect(Collectors.toList());
            CommissionType type = CommissionType.fromXmlValue(x.getCommission().getType());
            String mmName = marketMakerByEventId.get(x.getId());

            if (x.getMethod().getLmsr() != null) {
                result.add(new LmsrEvent(x.getId(), x.getName().trim(), x.getDescription().trim(),
                        x.getCommission().getValue(), type, options, mmName, x.getMethod().getLmsr().getB()));
            } else {
                GmOrderBookXml ob = x.getMethod().getOrderBook();
                boolean allowMint = Boolean.parseBoolean(ob.getAllowMint());
                result.add(new OrderBookEvent(x.getId(), x.getName().trim(), x.getDescription().trim(),
                        x.getCommission().getValue(), type, options, mmName, ob.getD(), ob.getInitial(), allowMint));
            }
        }
        return result;
    }

    private List<User> buildUsers(List<GmUserXml> xmlUsers) {
        List<User> result = new ArrayList<>();
        for (GmUserXml x : xmlUsers) {
            User user = new User(x.getName().trim(), x.getInitialCash());
            if (x.getMarketMaker() != null) {
                for (GmEventRefXml ref : x.getMarketMaker().getEvent()) {
                    user.addMarketMakerOf(ref.getId());
                }
            }
            result.add(user);
        }
        return result;
    }
}
