package gm.engine;

import gm.engine.dto.BalancePointDto;
import gm.engine.dto.CloseEventResultDto;
import gm.engine.dto.CreateEventSpecDto;
import gm.engine.dto.EventDetailDto;
import gm.engine.dto.EventFilter;
import gm.engine.dto.EventSummaryDto;
import gm.engine.dto.LoadResultDto;
import gm.engine.dto.OrderPlacementResultDto;
import gm.engine.dto.PricePointDto;
import gm.engine.dto.PurchaseResultDto;
import gm.engine.dto.UserDetailDto;
import gm.engine.dto.UserSummaryDto;
import gm.engine.exception.GmFileException;
import gm.engine.exception.GmOperationException;
import gm.engine.model.OrderSide;

import java.util.List;

/**
 * Everything the UI layer is allowed to know about the Guess Market engine. The UI never talks to
 * concrete engine/model classes directly - only through this interface and the DTOs it returns.
 * Every user-facing option/event number here is 1-based, matching the rest of the assignment's UI
 * conventions; the engine translates to/from 0-based indices internally.
 */
public interface GmEngine {

    /**
     * Loads and validates an XML events+users file, replacing whatever was loaded before.
     * A failed attempt never touches the previously loaded (valid) data.
     *
     * @throws GmFileException if the path, the XML, or its content is invalid
     */
    LoadResultDto loadEventsFile(String path);

    /** @return true once a valid file has been loaded at least once. */
    boolean isFileLoaded();

    List<UserSummaryDto> getUsers();

    /** @throws GmOperationException if no such user exists */
    UserDetailDto getUserDetail(String username);

    List<EventSummaryDto> getEvents(EventFilter filter);

    /** @throws GmOperationException if no such event exists */
    EventDetailDto getEventDetail(int eventId);

    /** @throws GmOperationException if the user isn't this event's market maker, or it's already open */
    EventDetailDto openEvent(String marketMakerUsername, int eventId);

    /** @throws GmOperationException if the user isn't this event's market maker, or it isn't active */
    CloseEventResultDto closeEvent(String marketMakerUsername, int eventId, int winningOptionNumber);

    /** LMSR purchase. @throws GmOperationException if the event isn't LMSR, isn't active, or funds are insufficient */
    PurchaseResultDto buyLmsrShares(String username, int eventId, int optionNumber, double quantity);

    /** Order-book order. @throws GmOperationException if the event isn't order-book, isn't active, or the order is invalid */
    OrderPlacementResultDto placeOrder(String username, int eventId, int optionNumber, OrderSide side,
                                        double quantity, double price);

    /** Bonus: creates a brand-new event with the acting user as its market maker. */
    EventDetailDto createEvent(String marketMakerUsername, CreateEventSpecDto spec);

    /** Bonus: chart data - executed trade prices for one option, in chronological order. */
    List<PricePointDto> getPriceHistory(int eventId, int optionNumber);

    /** Bonus: chart data - a user's account balance over time. */
    List<BalancePointDto> getBalanceHistory(String username);
}
