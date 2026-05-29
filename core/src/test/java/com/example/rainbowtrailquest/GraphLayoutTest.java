package com.example.rainbowtrailquest;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Tests for the branching graph layouts and fork-choice mechanics (Phase 2). */
public class GraphLayoutTest {

    private GameEngine layout(String id) {
        GameEngine e = new GameEngine(new Random(7));
        e.newGame(id);
        return e;
    }

    private final String[] graphLayouts = {"candy", "dino", "mermaid", "tree"};

    // ── structure ────────────────────────────────────────────────

    @Test
    public void testGraphLayoutsHaveBranches() {
        for (String id : graphLayouts) {
            assertTrue(id + " should branch", layout(id).hasBranches);
        }
    }

    @Test
    public void testClassicHasNoBranches() {
        GameEngine e = new GameEngine(new Random(7));
        e.newGame(); // classic
        assertFalse(e.hasBranches);
        assertEquals("classic", e.getLayoutId());
    }

    @Test
    public void testFirstSpaceIsStartLastIsCastle() {
        for (String id : graphLayouts) {
            GameEngine e = layout(id);
            assertEquals(id, "Start", e.board.get(0).specialName);
            GameEngine.Space last = e.board.get(e.board.size() - 1);
            assertTrue(id + " last is castle", last.castle);
            assertEquals("Castle", last.specialName);
            assertTrue(id + " castle is a dead end", last.nextIds.isEmpty());
        }
    }

    @Test
    public void testThreeNamedLandmarksAndDeckSize() {
        for (String id : graphLayouts) {
            GameEngine e = layout(id);
            int count = 0;
            for (GameEngine.Space s : e.board) {
                if (s.specialName != null && !s.specialName.equals("Start") && !s.specialName.equals("Castle")) count++;
            }
            assertEquals(id + " has 3 landmarks", 3, count);
            assertEquals(id + " deck size", 39, e.deck.size());
        }
    }

    @Test
    public void testAllEdgesPointToValidSpaces() {
        for (String id : graphLayouts) {
            GameEngine e = layout(id);
            int n = e.board.size();
            for (GameEngine.Space s : e.board)
                for (int nxt : s.nextIds)
                    assertTrue(id + " edge in bounds", nxt > 0 && nxt < n);
        }
    }

    @Test
    public void testCastleReachableFromStart() {
        for (String id : graphLayouts) {
            GameEngine e = layout(id);
            int castle = e.board.size() - 1;
            // BFS over edges from 0
            boolean[] seen = new boolean[e.board.size()];
            Deque<Integer> q = new ArrayDeque<>();
            q.add(0); seen[0] = true;
            boolean reached = false;
            while (!q.isEmpty()) {
                int cur = q.poll();
                if (cur == castle) { reached = true; break; }
                for (int nxt : e.board.get(cur).nextIds)
                    if (!seen[nxt]) { seen[nxt] = true; q.add(nxt); }
            }
            assertTrue(id + " castle reachable", reached);
        }
    }

    @Test
    public void testGraphIsAcyclic() {
        for (String id : graphLayouts) {
            GameEngine e = layout(id);
            int n = e.board.size();
            int[] state = new int[n]; // 0=unseen,1=on-stack,2=done
            for (int i = 0; i < n; i++) assertFalse(id + " has a cycle", hasCycle(e, i, state));
        }
    }
    private boolean hasCycle(GameEngine e, int u, int[] state) {
        if (state[u] == 1) return true;
        if (state[u] == 2) return false;
        state[u] = 1;
        for (int v : e.board.get(u).nextIds) if (hasCycle(e, v, state)) return true;
        state[u] = 2;
        return false;
    }

    // ── fork-choice mechanics ────────────────────────────────────

    @Test
    public void testForkPausesThenChoiceResolves() {
        // Play until a fork is pending, then resolve it.
        GameEngine e = layout("tree");
        boolean sawPending = false;
        for (int i = 0; i < 60 && !e.isWon(); i++) {
            if (e.isBranchPending()) {
                sawPending = true;
                List<Integer> opts = e.getBranchOptions();
                assertTrue(opts.size() >= 2);
                int fork = e.getBranchNode();
                int chosen = opts.get(0);
                e.chooseBranch(chosen);
                assertFalse("choice clears pending (or a new fork begins)",
                        e.isBranchPending() && e.getBranchNode() == fork && e.getPlayerPosition() == fork);
            } else {
                e.drawCard();
            }
        }
        assertTrue("a fork should arise on the tree board", sawPending);
    }

    @Test
    public void testDrawIgnoredWhileBranchPending() {
        GameEngine e = layout("mermaid");
        for (int i = 0; i < 80; i++) {
            e.drawCard();
            if (e.isBranchPending()) break;
        }
        assertTrue("expected a pending fork", e.isBranchPending());
        int pos = e.getPlayerPosition();
        int mv = e.getMoveCount();
        e.drawCard(); // should be a no-op while pending
        assertEquals(pos, e.getPlayerPosition());
        assertEquals(mv, e.getMoveCount());
    }

    @Test
    public void testInvalidBranchChoiceIgnored() {
        GameEngine e = layout("mermaid");
        for (int i = 0; i < 80 && !e.isBranchPending(); i++) e.drawCard();
        assertTrue(e.isBranchPending());
        int pos = e.getPlayerPosition();
        e.chooseBranch(99999); // not an option
        assertTrue(e.isBranchPending());
        assertEquals(pos, e.getPlayerPosition());
    }

    @Test
    public void testChoosingMovesAlongChosenEdge() {
        GameEngine e = layout("dino");
        for (int i = 0; i < 80 && !e.isBranchPending(); i++) e.drawCard();
        assertTrue(e.isBranchPending());
        int fork = e.getBranchNode();
        int chosen = e.getBranchOptions().get(0);
        e.chooseBranch(chosen);
        // After choosing, the player must have left the fork (unless immediately stopped at another fork)
        assertTrue(e.getPlayerPosition() != fork || e.isBranchPending());
    }

    @Test
    public void testWinReachableOnGraph() {
        GameEngine e = layout("candy");
        int castle = e.board.size() - 1;
        // find a predecessor of the castle and stand the player there
        int pred = -1;
        for (int i = 0; i < e.board.size(); i++)
            if (e.board.get(i).nextIds.contains(castle)) { pred = i; break; }
        assertTrue(pred >= 0);
        e.player = pred;
        e.deck.clear();
        e.deck.add(new GameEngine.Card(0, 1, null)); // castle matches any colour
        e.drawCard();
        assertTrue(e.isWon());
    }

    @Test
    public void testMoveCountIncrementsOncePerCardAcrossChoice() {
        GameEngine e = layout("tree");
        for (int i = 0; i < 80 && !e.isBranchPending(); i++) e.drawCard();
        assertTrue(e.isBranchPending());
        int mv = e.getMoveCount();
        e.chooseBranch(e.getBranchOptions().get(0));
        // resolving a fork is part of the same card — must not add a move
        assertEquals(mv, e.getMoveCount());
    }

    @Test
    public void testEveryLayoutPlaysToCompletion() {
        for (String id : graphLayouts) {
            GameEngine e = layout(id);
            int guard = 0;
            while (!e.isWon() && guard++ < 2000) {
                if (e.isBranchPending()) e.chooseBranch(e.getBranchOptions().get(guard % e.getBranchOptions().size()));
                else e.drawCard();
            }
            assertTrue(id + " should be winnable", e.isWon());
        }
    }

    @Test
    public void testNewGameByIdResetsState() {
        GameEngine e = layout("tree");
        for (int i = 0; i < 30; i++) e.drawCard();
        e.newGame("mermaid");
        assertEquals(0, e.getPlayerPosition());
        assertEquals(0, e.getMoveCount());
        assertFalse(e.isBranchPending());
        assertEquals("mermaid", e.getLayoutId());
    }
}
