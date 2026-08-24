package gm.engine;

import gm.engine.dto.LoadResultDto;
import gm.engine.exception.GmFileException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GmFileLoaderTest {

    private static final String TESTFILES = "../testfiles/";

    @Test
    void loadsSmallFile() {
        GmEngineImpl engine = new GmEngineImpl();
        LoadResultDto result = engine.loadEventsFile(TESTFILES + "small.xml");
        assertEquals(2, result.getEventCount());
        assertEquals(3, result.getUserCount());
    }

    @Test
    void loadsMultipleFile() {
        GmEngineImpl engine = new GmEngineImpl();
        LoadResultDto result = engine.loadEventsFile(TESTFILES + "multiple.xml");
        assertEquals(4, result.getEventCount());
        assertEquals(3, result.getUserCount());
    }

    @Test
    void rejectsUnknownMarketMakerEventReference() {
        GmEngineImpl engine = new GmEngineImpl();
        GmFileException ex = assertThrows(GmFileException.class, () -> engine.loadEventsFile(TESTFILES + "error-3.xml"));
        assertTrue(ex.getMessage().contains("market maker of event id 12"), ex.getMessage());
    }

    @Test
    void rejectsZeroInitialCash() {
        GmEngineImpl engine = new GmEngineImpl();
        GmFileException ex = assertThrows(GmFileException.class, () -> engine.loadEventsFile(TESTFILES + "error-2.xml"));
        assertTrue(ex.getMessage().contains("initial cash balance of 0"), ex.getMessage());
    }

    @Test
    void rejectsMissingFile() {
        GmEngineImpl engine = new GmEngineImpl();
        assertThrows(GmFileException.class, () -> engine.loadEventsFile(TESTFILES + "does-not-exist.xml"));
    }

    @Test
    void failedLoadDoesNotReplacePreviousData() {
        GmEngineImpl engine = new GmEngineImpl();
        engine.loadEventsFile(TESTFILES + "small.xml");
        assertThrows(GmFileException.class, () -> engine.loadEventsFile(TESTFILES + "error-2.xml"));
        assertEquals(2, engine.getEvents(gm.engine.dto.EventFilter.all()).size());
    }
}
