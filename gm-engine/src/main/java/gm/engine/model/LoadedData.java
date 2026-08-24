package gm.engine.model;

import java.util.List;

/** Everything a valid XML file produces: the events and the users who trade/manage them. */
public class LoadedData {

    private final List<Event> events;
    private final List<User> users;

    public LoadedData(List<Event> events, List<User> users) {
        this.events = events;
        this.users = users;
    }

    public List<Event> getEvents() {
        return events;
    }

    public List<User> getUsers() {
        return users;
    }
}
