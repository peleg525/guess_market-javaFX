package gm.engine;

import gm.engine.dto.BalancePointDto;
import gm.engine.dto.CloseEventResultDto;
import gm.engine.dto.CreateEventSpecDto;
import gm.engine.dto.EventDetailDto;
import gm.engine.dto.EventFilter;
import gm.engine.dto.EventSummaryDto;
import gm.engine.dto.LmsrEventDetailDto;
import gm.engine.dto.LoadResultDto;
import gm.engine.dto.OptionBookDto;
import gm.engine.dto.OptionStatusDto;
import gm.engine.dto.OrderBookEventDetailDto;
import gm.engine.dto.OrderBookStatsDto;
import gm.engine.dto.OrderPlacementResultDto;
import gm.engine.dto.OrderRowDto;
import gm.engine.dto.ParticipantHoldingDto;
import gm.engine.dto.ParticipationSummaryDto;
import gm.engine.dto.PricePointDto;
import gm.engine.dto.PurchaseResultDto;
import gm.engine.dto.TradeDto;
import gm.engine.dto.TradeMethod;
import gm.engine.dto.UserDetailDto;
import gm.engine.dto.UserSummaryDto;
import gm.engine.exception.GmOperationException;
import gm.engine.model.AccountMover;
import gm.engine.model.Event;
import gm.engine.model.EventStatus;
import gm.engine.model.Holding;
import gm.engine.model.LmsrEvent;
import gm.engine.model.LoadedData;
import gm.engine.model.Order;
import gm.engine.model.OrderBookEvent;
import gm.engine.model.OrderPlacementResult;
import gm.engine.model.OrderSide;
import gm.engine.model.PurchaseOutcome;
import gm.engine.model.SettlementResult;
import gm.engine.model.Trade;
import gm.engine.model.User;
import gm.engine.xml.GmFileLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class GmEngineImpl implements GmEngine {

    private final GmFileLoader fileLoader = new GmFileLoader();
    private final AtomicLong sequenceCounter = new AtomicLong();
    private final AtomicInteger nextEventId = new AtomicInteger();

    private List<Event> events = new ArrayList<>();
    private Map<String, User> usersByName = new LinkedHashMap<>();
    private boolean fileLoaded = false;

    @Override
    public LoadResultDto loadEventsFile(String path) {
        LoadedData data = fileLoader.load(path);
        this.events = data.getEvents();
        this.usersByName = new LinkedHashMap<>();
        for (User user : data.getUsers()) {
            usersByName.put(user.getName(), user);
        }
        this.fileLoaded = true;
        int maxId = events.stream().mapToInt(Event::getId).max().orElse(0);
        nextEventId.set(maxId + 1);
        return new LoadResultDto(events.size(), usersByName.size());
    }

    @Override
    public boolean isFileLoaded() {
        return fileLoaded;
    }

    @Override
    public List<UserSummaryDto> getUsers() {
        requireFileLoaded();
        return usersByName.values().stream().map(this::toUserSummary).collect(Collectors.toList());
    }

    @Override
    public UserDetailDto getUserDetail(String username) {
        User user = findUser(username);
        List<ParticipationSummaryDto> participations = new ArrayList<>();
        for (Event event : events) {
            boolean isMm = user.isMarketMakerOf(event.getId());
            boolean hasPosition = eventHolding(event, username) != null && eventHolding(event, username).hasAnyPosition();
            boolean hasRestingOrder = event instanceof OrderBookEvent && hasRestingOrder((OrderBookEvent) event, username);
            if (isMm || hasPosition || hasRestingOrder) {
                participations.add(new ParticipationSummaryDto(event.getId(), event.getName(), methodOf(event),
                        event.getStatus(), isMm));
            }
        }
        return new UserDetailDto(user.getName(), user.getAccount().getBalance(), user.getAccount().isBlocked(), participations);
    }

    @Override
    public List<EventSummaryDto> getEvents(EventFilter filter) {
        requireFileLoaded();
        EventFilter effectiveFilter = filter == null ? EventFilter.all() : filter;
        return events.stream()
                .filter(e -> effectiveFilter.matchesMethod(methodOf(e))
                        && effectiveFilter.matchesStatus(e.getStatus())
                        && effectiveFilter.matchesCommissionType(e.getCommissionType().getXmlValue()))
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public EventDetailDto getEventDetail(int eventId) {
        return toDetail(findEvent(eventId));
    }

    @Override
    public EventDetailDto openEvent(String marketMakerUsername, int eventId) {
        User user = findUser(marketMakerUsername);
        Event event = findEvent(eventId);
        long seq = nextSequence();
        if (event instanceof LmsrEvent lmsr) {
            lmsr.open(user, seq);
        } else {
            ((OrderBookEvent) event).open(user, seq);
        }
        return toDetail(event);
    }

    @Override
    public CloseEventResultDto closeEvent(String marketMakerUsername, int eventId, int winningOptionNumber) {
        User user = findUser(marketMakerUsername);
        Event event = findEvent(eventId);
        int winningIndex = winningOptionNumber - 1;
        long seq = nextSequence();

        SettlementResult result = (event instanceof LmsrEvent lmsr)
                ? lmsr.close(user, winningIndex, seq)
                : ((OrderBookEvent) event).close(user, winningIndex, seq);

        for (Map.Entry<String, Double> payout : result.getWinnerPayouts().entrySet()) {
            User winner = usersByName.get(payout.getKey());
            winner.getAccount().credit(payout.getValue(), "Payout from event '" + event.getName() + "'", seq);
        }

        return new CloseEventResultDto(toDetail(event), result.isMarketMakerBlocked());
    }

    @Override
    public PurchaseResultDto buyLmsrShares(String username, int eventId, int optionNumber, double quantity) {
        User user = findUser(username);
        Event event = findEvent(eventId);
        if (!(event instanceof LmsrEvent lmsr)) {
            throw new GmOperationException("Event '" + event.getName() + "' is not an LMSR event.");
        }
        PurchaseOutcome outcome = lmsr.buy(user, optionNumber - 1, quantity, nextSequence());
        return new PurchaseResultDto(outcome.getSharesCost(), outcome.getCommissionPaid(), buildLmsrDetail(lmsr));
    }

    @Override
    public OrderPlacementResultDto placeOrder(String username, int eventId, int optionNumber, OrderSide side,
                                               double quantity, double price) {
        User user = findUser(username);
        Event event = findEvent(eventId);
        if (!(event instanceof OrderBookEvent orderBook)) {
            throw new GmOperationException("Event '" + event.getName() + "' is not an order-book event.");
        }
        AccountMover mover = new EngineAccountMover();
        OrderPlacementResult result = orderBook.placeOrder(user, optionNumber - 1, side, quantity, price,
                nextSequence(), mover);
        return new OrderPlacementResultDto(result.getQuantityFilled(), result.getQuantityResting(),
                result.getNewlyBlockedUsernames(), buildOrderBookDetail(orderBook));
    }

    @Override
    public EventDetailDto createEvent(String marketMakerUsername, CreateEventSpecDto spec) {
        User user = findUser(marketMakerUsername);
        int id = nextEventId.getAndIncrement();
        List<String> options = List.of(spec.getOption1().trim(), spec.getOption2().trim());
        gm.engine.model.CommissionType type = gm.engine.model.CommissionType.fromXmlValue(spec.getCommissionType());

        Event event;
        if (spec.getMethod() == TradeMethod.LMSR) {
            event = new LmsrEvent(id, spec.getName().trim(), spec.getDescription().trim(), spec.getCommissionPercent(),
                    type, options, marketMakerUsername, spec.getLmsrB());
        } else {
            event = new OrderBookEvent(id, spec.getName().trim(), spec.getDescription().trim(), spec.getCommissionPercent(),
                    type, options, marketMakerUsername, spec.getOrderBookD(), spec.getOrderBookInitial(),
                    Boolean.TRUE.equals(spec.getOrderBookAllowMint()));
        }
        events.add(event);
        user.addMarketMakerOf(id);
        return toDetail(event);
    }

    @Override
    public List<PricePointDto> getPriceHistory(int eventId, int optionNumber) {
        Event event = findEvent(eventId);
        int optionIndex = optionNumber - 1;
        List<Trade> trades = (event instanceof LmsrEvent lmsr) ? lmsr.getTradeHistory()
                : ((OrderBookEvent) event).getTradeHistory();
        List<PricePointDto> points = new ArrayList<>();
        for (Trade trade : trades) {
            if (trade.getOptionIndex() == optionIndex) {
                double pricePerShare = trade.getQuantity() == 0 ? 0 : trade.getPricePaid() / trade.getQuantity();
                points.add(new PricePointDto(trade.getSequence(), pricePerShare));
            }
        }
        return points;
    }

    @Override
    public List<BalancePointDto> getBalanceHistory(String username) {
        User user = findUser(username);
        return user.getAccount().getHistory().stream()
                .map(entry -> new BalancePointDto(entry.getSequence(), entry.getBalanceAfter(), entry.getReason()))
                .collect(Collectors.toList());
    }

    // ---- lookups ----

    private void requireFileLoaded() {
        if (!fileLoaded) {
            throw new GmOperationException("No valid events file has been loaded yet.");
        }
    }

    private User findUser(String username) {
        requireFileLoaded();
        User user = usersByName.get(username);
        if (user == null) {
            throw new GmOperationException("No user found with name '" + username + "'.");
        }
        return user;
    }

    private Event findEvent(int eventId) {
        requireFileLoaded();
        return events.stream().filter(e -> e.getId() == eventId).findFirst()
                .orElseThrow(() -> new GmOperationException("No event found with id " + eventId + "."));
    }

    private long nextSequence() {
        return sequenceCounter.incrementAndGet();
    }

    private TradeMethod methodOf(Event event) {
        return event instanceof LmsrEvent ? TradeMethod.LMSR : TradeMethod.ORDER_BOOK;
    }

    private Holding eventHolding(Event event, String username) {
        return event instanceof LmsrEvent lmsr ? lmsr.getHolding(username) : ((OrderBookEvent) event).getHolding(username);
    }

    private boolean hasRestingOrder(OrderBookEvent event, String username) {
        for (int i = 0; i < event.getOptions().size(); i++) {
            if (event.getBids(i).stream().anyMatch(o -> o.getOwnerUsername().equals(username))) {
                return true;
            }
            if (event.getAsks(i).stream().anyMatch(o -> o.getOwnerUsername().equals(username))) {
                return true;
            }
        }
        return false;
    }

    // ---- DTO building ----

    private UserSummaryDto toUserSummary(User user) {
        return new UserSummaryDto(user.getName(), user.getAccount().getBalance(), user.getAccount().isBlocked(),
                !user.getMarketMakerOfEventIds().isEmpty());
    }

    private EventSummaryDto toSummary(Event event) {
        return new EventSummaryDto(event.getId(), event.getName(), event.getDescription(),
                event.getCommissionPercent(), event.getCommissionType().getXmlValue(), methodOf(event),
                event.getStatus(), event.getMarketMakerUsername(), event.getOptions());
    }

    private EventDetailDto toDetail(Event event) {
        return event instanceof LmsrEvent lmsr ? buildLmsrDetail(lmsr) : buildOrderBookDetail((OrderBookEvent) event);
    }

    private LmsrEventDetailDto buildLmsrDetail(LmsrEvent event) {
        List<OptionStatusDto> optionStatuses = new ArrayList<>();
        List<String> options = event.getOptions();
        for (int i = 0; i < options.size(); i++) {
            optionStatuses.add(new OptionStatusDto(options.get(i), event.currentPrice(i), event.totalSharesBought(i)));
        }

        List<TradeDto> history = new ArrayList<>();
        for (Trade trade : event.getTradeHistory()) {
            history.add(new TradeDto(trade.getSequence(), trade.getOwnerUsername(),
                    options.get(trade.getOptionIndex()), trade.getQuantity(), trade.getPricePaid(), trade.getCommissionPaid()));
        }
        Collections.reverse(history);

        String winningOptionName = event.getStatus() == EventStatus.CLOSED && event.getWinningOptionIndex() != null
                ? options.get(event.getWinningOptionIndex()) : null;

        return new LmsrEventDetailDto(event.getId(), event.getName(), event.getDescription(),
                event.getCommissionPercent(), event.getCommissionType().getXmlValue(), event.getStatus(),
                event.getMarketMakerUsername(), options, winningOptionName, event.getTotalCommissionCollected(),
                event.getB(), event.getEventAccountBalance(), optionStatuses, history);
    }

    private OrderBookEventDetailDto buildOrderBookDetail(OrderBookEvent event) {
        List<String> options = event.getOptions();
        List<OptionBookDto> optionBooks = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            List<OrderRowDto> bids = event.getBids(i).stream()
                    .map(o -> new OrderRowDto(o.getOwnerUsername(), o.getRemainingQuantity(), o.getPrice()))
                    .collect(Collectors.toList());
            List<OrderRowDto> asks = event.getAsks(i).stream()
                    .map(o -> new OrderRowDto(o.getOwnerUsername(), o.getRemainingQuantity(), o.getPrice()))
                    .collect(Collectors.toList());
            OrderBookStatsDto stats = new OrderBookStatsDto(event.getLastTradePrice(i), event.getBestBid(i), event.getBestAsk(i));
            optionBooks.add(new OptionBookDto(options.get(i), bids, asks, stats));
        }

        List<ParticipantHoldingDto> participants = new ArrayList<>();
        for (Map.Entry<String, Holding> entry : event.getAllHoldings().entrySet()) {
            if (!entry.getValue().hasAnyPosition()) {
                continue;
            }
            List<Double> quantities = new ArrayList<>();
            List<Double> amounts = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                quantities.add(entry.getValue().getQuantity(i));
                amounts.add(entry.getValue().getAmountPaid(i));
            }
            participants.add(new ParticipantHoldingDto(entry.getKey(), quantities, amounts));
        }

        String winningOptionName = event.getStatus() == EventStatus.CLOSED && event.getWinningOptionIndex() != null
                ? options.get(event.getWinningOptionIndex()) : null;

        return new OrderBookEventDetailDto(event.getId(), event.getName(), event.getDescription(),
                event.getCommissionPercent(), event.getCommissionType().getXmlValue(), event.getStatus(),
                event.getMarketMakerUsername(), options, winningOptionName, event.getTotalCommissionCollected(),
                event.getD(), event.getInitial(), event.isAllowMint(), event.getEventAccountBalance(),
                optionBooks, participants);
    }

    private class EngineAccountMover implements AccountMover {
        @Override
        public void credit(String username, double amount, String reason, long sequence) {
            usersByName.get(username).getAccount().credit(amount, reason, sequence);
        }

        @Override
        public boolean chargeLeniently(String username, double amount, String reason, long sequence) {
            return usersByName.get(username).getAccount().settle(-amount, reason, sequence);
        }
    }
}
