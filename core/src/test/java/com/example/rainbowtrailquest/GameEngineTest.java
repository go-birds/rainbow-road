package com.example.rainbowtrailquest;

import org.junit.Before;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class GameEngineTest {
    private GameEngine engine;

    @Before
    public void setUp() {
        engine = new GameEngine(new Random(42));
        engine.newGame();
    }

    // --- Board construction ---

    @Test
    public void testBoardSizeBetween45And60() {
        int size = engine.board.size();
        assertTrue(size >= 45 && size <= 60);
    }

    @Test
    public void testFirstSpaceIsStart() {
        assertEquals("Start", engine.board.get(0).specialName);
    }

    @Test
    public void testLastSpaceIsCastle() {
        GameEngine.Space last = engine.board.get(engine.board.size() - 1);
        assertTrue(last.castle);
        assertEquals("Castle", last.specialName);
    }

    @Test
    public void testBoardColorsAreCyclic() {
        for (int i = 0; i < 12; i++) {
            assertEquals(i % 6, engine.board.get(i).colorIndex);
        }
    }

    @Test
    public void testExactlyThreeNamedPlaces() {
        int count = 0;
        for (GameEngine.Space s : engine.board) {
            if (s.specialName != null && !s.specialName.equals("Start") && !s.specialName.equals("Castle")) {
                count++;
            }
        }
        assertEquals(3, count);
    }

    @Test
    public void testNoTwoSpacesShareASpecialName() {
        Set<String> seen = new HashSet<>();
        for (GameEngine.Space s : engine.board) {
            if (s.specialName != null) {
                assertFalse("Duplicate specialName: " + s.specialName, seen.contains(s.specialName));
                seen.add(s.specialName);
            }
        }
    }

    // --- Deck construction ---

    @Test
    public void testDeckSize() {
        assertEquals(39, engine.deck.size());
    }

    @Test
    public void testDeckCardCounts() {
        int singles = 0, doubles = 0, specials = 0;
        for (GameEngine.Card card : engine.deck) {
            if (card.special != null) specials++;
            else if (card.count == 1) singles++;
            else doubles++;
        }
        assertEquals(24, singles);
        assertEquals(12, doubles);
        assertEquals(3, specials);
    }

    @Test
    public void testSpecialCardsMatchBoardPlaces() {
        Set<String> boardPlaces = new HashSet<>();
        for (GameEngine.Space s : engine.board) {
            if (s.specialName != null && !s.specialName.equals("Start") && !s.specialName.equals("Castle")) {
                boardPlaces.add(s.specialName);
            }
        }
        Set<String> cardPlaces = new HashSet<>();
        for (GameEngine.Card card : engine.deck) {
            if (card.special != null) cardPlaces.add(card.special);
        }
        assertEquals(boardPlaces, cardPlaces);
    }

    // --- findNextColor ---

    @Test
    public void testFindNextColorSingleHit() {
        // colorIndex 0 repeats every 6 spaces: 0, 6, 12, ...
        assertEquals(6, engine.findNextColor(1, 0, 1));
    }

    @Test
    public void testFindNextColorDoubleHit() {
        assertEquals(12, engine.findNextColor(1, 0, 2));
    }

    @Test
    public void testFindNextColorFallsBackToCastle() {
        // Starting past the end of the board triggers the fallback return
        int result = engine.findNextColor(engine.board.size(), 0, 1);
        assertEquals(engine.board.size() - 1, result);
    }

    @Test
    public void testFindNextColorCastleAlwaysMatches() {
        // Castle matches regardless of colorIndex due to the `|| castle` condition
        int lastIdx = engine.board.size() - 1;
        int result = engine.findNextColor(lastIdx, 99, 1);
        assertEquals(lastIdx, result);
    }

    // --- pickSpecialIndexes ---

    @Test
    public void testPickSpecialIndexesLength() {
        int[] result = engine.pickSpecialIndexes(50);
        assertEquals(3, result.length);
    }

    @Test
    public void testPickSpecialIndexesAscending() {
        int[] result = engine.pickSpecialIndexes(50);
        assertTrue(result[0] < result[1]);
        assertTrue(result[1] < result[2]);
    }

    @Test
    public void testPickSpecialIndexesInBounds() {
        int total = 50;
        int[] result = engine.pickSpecialIndexes(total);
        for (int idx : result) {
            assertTrue(idx >= 0 && idx < total);
        }
    }

    @Test
    public void testPickSpecialIndexesMinSeparation() {
        int[] result = engine.pickSpecialIndexes(50);
        assertTrue(result[1] >= result[0] + 5);
    }

    // --- indexOfSpecial ---

    @Test
    public void testIndexOfSpecialFindsNamedPlace() {
        for (int i = 0; i < engine.board.size(); i++) {
            GameEngine.Space s = engine.board.get(i);
            if (s.specialName != null && !s.specialName.equals("Start") && !s.specialName.equals("Castle")) {
                assertEquals(i, engine.indexOfSpecial(s.specialName));
                return;
            }
        }
        fail("No named place found on board");
    }

    @Test
    public void testIndexOfSpecialMissingNameReturnsPlayerPos() {
        int pos = engine.getPlayerPosition();
        assertEquals(pos, engine.indexOfSpecial("Nonexistent Place"));
    }

    // --- drawCard ---

    @Test
    public void testDrawCardIncrementsMoveCount() {
        engine.drawCard();
        assertEquals(1, engine.getMoveCount());
    }

    @Test
    public void testDrawCardMovesPlayerNonNegative() {
        engine.drawCard();
        assertTrue(engine.getPlayerPosition() >= 0);
    }

    @Test
    public void testDrawCardSetsNonEmptyLastCard() {
        engine.drawCard();
        assertNotNull(engine.getLastCard());
        assertFalse(engine.getLastCard().isEmpty());
    }

    @Test
    public void testWinConditionSet() {
        engine.player = engine.board.size() - 2;
        engine.deck.clear();
        engine.deck.add(new GameEngine.Card(0, 1, null));
        engine.drawCard();
        assertTrue(engine.isWon());
    }

    @Test
    public void testDrawCardRebuildsEmptyDeck() {
        engine.deck.clear();
        engine.drawCard();
        assertEquals(1, engine.getMoveCount());
    }

    @Test
    public void testSpecialCardTeleportsPlayer() {
        String targetName = null;
        int targetIndex = -1;
        for (int i = 0; i < engine.board.size(); i++) {
            GameEngine.Space s = engine.board.get(i);
            if (s.specialName != null && !s.specialName.equals("Start") && !s.specialName.equals("Castle")) {
                targetName = s.specialName;
                targetIndex = i;
                s.shortcut = false;
                s.sticky = false;
                break;
            }
        }
        assertNotNull(targetName);
        engine.deck.clear();
        engine.deck.add(new GameEngine.Card(-1, 0, targetName));
        engine.drawCard();
        assertEquals(targetIndex, engine.getPlayerPosition());
    }

    // --- newGame reset ---

    @Test
    public void testNewGameResetsPlayerAndMoves() {
        engine.drawCard();
        engine.drawCard();
        engine.newGame();
        assertEquals(0, engine.getPlayerPosition());
        assertEquals(0, engine.getMoveCount());
    }

    @Test
    public void testNewGameAssignsTheme() {
        assertNotNull(engine.getCurrentTheme());
        assertNotNull(engine.getCurrentTheme().name);
        assertFalse(engine.getCurrentTheme().name.isEmpty());
    }
}
