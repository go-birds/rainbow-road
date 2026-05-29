package com.example.rainbowtrailquest;

import java.util.*;

class GameEngine {
    private final Random random;
    final String[] colorNames = {"Pink", "Orange", "Lemon", "Mint", "Sky", "Grape"};
    private final Theme[] themes = {
        new Theme("Candy Cove", "🍬", "Lollipop Lagoon", "Gumdrop Garden", "Peppermint Pier"),
        new Theme("Dino Sprinkle Park", "🦕", "Fossil Frosting", "Volcano Vanilla", "T-Rex Taffy"),
        new Theme("Mermaid Sherbet Sea", "🧜", "Pearl Pop Reef", "Bubblegum Bay", "Coral Cupcake"),
        new Theme("Space Sundae Galaxy", "🚀", "Moon Marshmallow", "Comet Cookie", "Starlight Swirl"),
        new Theme("Unicorn Jellybean Woods", "🦄", "Rainbow Glade", "Sparkle Bridge", "Cloud Cake")
    };

    /** Selectable board layouts. "classic" = original random linear board. */
    static final String[] LAYOUTS = {"candy", "dino", "mermaid", "tree", "classic"};

    Theme theme;
    ArrayList<Space> board = new ArrayList<>();
    ArrayList<Card> deck = new ArrayList<>();
    int player = 0;
    int moves = 0;
    private String lastCard = "Tap Draw Card to start!";

    // graph-mode state
    String layoutId = "classic";
    boolean hasBranches = false;
    boolean branchPending = false;
    int branchNode = -1;
    final ArrayList<Integer> branchOptions = new ArrayList<>();
    private int pendColor, pendCount, pendHits, moveStartPos;
    private final ArrayList<Integer> trail = new ArrayList<>();

    GameEngine() { this.random = new Random(); }
    GameEngine(Random rng) { this.random = rng; }

    // ─────────────────────────────────────────────────────────────
    //  Game setup
    // ─────────────────────────────────────────────────────────────

    /** Classic random linear board (unchanged behaviour — default for tests). */
    void newGame() {
        layoutId = "classic";
        theme = themes[random.nextInt(themes.length)];
        buildBoard();
        finishSetup("Welcome to " + theme.name + " " + theme.emoji + " Draw a card!");
    }

    /** Start a specific layout by id (one of LAYOUTS). */
    void newGame(String id) {
        layoutId = id;
        board.clear();
        switch (id) {
            case "candy":   buildCandy();   break;
            case "dino":    buildDino();     break;
            case "mermaid": buildMermaid();  break;
            case "tree":    buildTree();     break;
            default:        layoutId = "classic";
                            theme = themes[random.nextInt(themes.length)];
                            buildBoard();    break;
        }
        finishSetup("Welcome to " + theme.name + " " + theme.emoji + " Draw a card!");
    }

    private void finishSetup(String message) {
        buildDeck();
        Collections.shuffle(deck, random);
        player = 0;
        moves = 0;
        branchPending = false;
        branchNode = -1;
        branchOptions.clear();
        trail.clear();
        trail.add(0);
        hasBranches = false;
        for (Space s : board) if (s.nextIds.size() > 1) { hasBranches = true; s.branch = true; }
        if (!board.isEmpty() && board.get(0).specialName == null) board.get(0).specialName = "Start";
        lastCard = message;
    }

    // ── classic linear board (positions + linear edges added for the renderer) ──
    void buildBoard() {
        board.clear();
        int total = 45 + random.nextInt(16);
        for (int i = 0; i < total; i++) {
            board.add(new Space(i % 6, null, false));
        }
        board.get(0).specialName = "Start";
        board.get(total - 1).specialName = "Castle";
        board.get(total - 1).castle = true;

        int[] spots = pickSpecialIndexes(total);
        board.get(spots[0]).specialName = theme.place1;
        board.get(spots[1]).specialName = theme.place2;
        board.get(spots[2]).specialName = theme.place3;

        for (int i = 1; i < board.size() - 1; i++) {
            if (random.nextDouble() < 0.08) board.get(i).shortcut = true;
            if (random.nextDouble() < 0.06) board.get(i).sticky = true;
        }
        // serpentine positions + linear edges (for graph-based renderer)
        int cols = 6;
        for (int i = 0; i < total; i++) {
            Space s = board.get(i);
            float t = (float) i / Math.max(total - 1, 1);
            s.py = 1f - t;
            s.px = 0.5f + 0.32f * (float) Math.sin(t * Math.PI * 4.5f);
            if (i + 1 < total) s.nextIds.add(i + 1);
        }
    }

    int[] pickSpecialIndexes(int total) {
        int a = 8 + random.nextInt(6);
        int b = total / 2 + random.nextInt(5) - 2;
        int c = total - 12 + random.nextInt(5);
        return new int[]{a, Math.max(a + 5, b), Math.min(total - 5, c)};
    }

    void buildDeck() {
        deck.clear();
        for (int c = 0; c < 6; c++) {
            for (int i = 0; i < 4; i++) deck.add(new Card(c, 1, null));
            for (int i = 0; i < 2; i++) deck.add(new Card(c, 2, null));
        }
        for (Space s : board) {
            if (s.specialName != null && !s.specialName.equals("Start") && !s.specialName.equals("Castle")) {
                deck.add(new Card(-1, 0, s.specialName));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Layout builders (graph topologies matching the mockups)
    // ─────────────────────────────────────────────────────────────

    private int add(int color, double px, double py, String name) {
        Space s = new Space(((color % 6) + 6) % 6, name, false);
        s.px = (float) px; s.py = (float) py;
        board.add(s);
        return board.size() - 1;
    }
    private void link(int a, int... bs) { for (int b : bs) board.get(a).nextIds.add(b); }
    /** Add a positioned chain of plain spaces and link them in order; returns their ids. */
    private int[] chain(int color0, double[][] pts) {
        int[] ids = new int[pts.length];
        for (int i = 0; i < pts.length; i++) ids[i] = add(color0 + i, pts[i][0], pts[i][1], null);
        for (int i = 0; i + 1 < ids.length; i++) link(ids[i], ids[i + 1]);
        return ids;
    }

    // Candy Cove — serpentine trail with two shortcut forks.
    private void buildCandy() {
        theme = themes[0];
        int N = 6, rows = 6;
        double mg = 0.16, sp = (1 - 2 * mg) / (N - 1);
        int[] path = new int[rows * N];
        int p = 0;
        for (int r = 0; r < rows; r++) {
            double py = 0.93 - r * (0.74 / (rows - 1));
            for (int c = 0; c < N; c++) {
                int col = (r % 2 == 0) ? c : (N - 1 - c);
                path[p] = add(p, mg + col * sp, py, null);
                p++;
            }
        }
        for (int i = 0; i + 1 < path.length; i++) link(path[i], path[i + 1]);
        int castle = add(2, 0.5, 0.07, "Castle");
        board.get(castle).castle = true;
        link(path[path.length - 1], castle);
        board.get(path[0]).specialName = "Start";
        board.get(path[8]).specialName  = theme.place1;  // Lollipop Lagoon
        board.get(path[21]).specialName = theme.place2;  // Gumdrop Garden
        board.get(path[33]).specialName = theme.place3;  // Peppermint Pier
        board.get(path[5]).sticky = true;
        board.get(path[27]).sticky = true;
        // shortcut forks: a gamble that skips ahead
        board.get(path[11]).shortcut = true; link(path[11], path[14]);
        board.get(path[23]).shortcut = true; link(path[23], path[31]);
    }

    // Dino Isle — shore → fork → jungle / cliff trails → reunite → summit castle.
    private void buildDino() {
        theme = themes[1];
        int[] base = chain(0, new double[][]{{0.5,0.86},{0.5,0.78},{0.5,0.70}});
        int cross = base[2];
        int[] jungle = chain(2, new double[][]{{0.37,0.62},{0.27,0.56},{0.20,0.49},{0.17,0.41},{0.21,0.34},{0.29,0.28}});
        int[] cliff  = chain(3, new double[][]{{0.63,0.62},{0.73,0.56},{0.80,0.49},{0.83,0.41},{0.79,0.34},{0.71,0.28}});
        link(cross, jungle[0], cliff[0]);                 // the fork
        board.get(jungle[4]).specialName = theme.place3;  // T-Rex Taffy
        board.get(jungle[1]).sticky = true;               // tar pit
        board.get(cliff[3]).specialName  = theme.place2;  // Volcano Vanilla
        int summit = add(2, 0.5, 0.235, theme.place1);     // Fossil Frosting (reunion)
        link(jungle[5], summit); link(cliff[5], summit);
        int s1 = add(4, 0.5, 0.165, null); link(summit, s1);
        int castle = add(2, 0.5, 0.085, "Castle");
        board.get(castle).castle = true; link(s1, castle);
        // cave shortcut on the jungle trail
        board.get(jungle[1]).shortcut = true; link(jungle[1], jungle[4]);
    }

    // Mermaid Deep — start → fork → two coral clusters → reef → 3-way → surface castle.
    private void buildMermaid() {
        theme = themes[2];
        int start = add(4, 0.5, 0.92, "Start");
        int pre = add(3, 0.5, 0.85, null); link(start, pre);
        int[] lc = chain(0, new double[][]{{0.30,0.79},{0.18,0.72},{0.13,0.63},{0.18,0.54},{0.30,0.47}});
        int[] rc = chain(1, new double[][]{{0.70,0.79},{0.82,0.72},{0.87,0.63},{0.82,0.54},{0.70,0.47}});
        link(pre, lc[0], rc[0]);                          // first fork
        board.get(lc[2]).specialName = theme.place3;      // Coral Cupcake
        board.get(rc[2]).specialName = theme.place2;      // Bubblegum Bay
        int reef = add(4, 0.5, 0.40, theme.place1);        // Pearl Pop Reef (reunion + 3-way)
        link(lc[4], reef); link(rc[4], reef);
        int[] ul = chain(2, new double[][]{{0.24,0.32},{0.17,0.23},{0.22,0.15}});
        int[] uc = chain(4, new double[][]{{0.50,0.31},{0.50,0.22},{0.50,0.14}});
        int[] ur = chain(1, new double[][]{{0.76,0.32},{0.83,0.23},{0.78,0.15}});
        link(reef, ul[0], uc[0], ur[0]);                  // three-way fork
        int join = add(3, 0.5, 0.085, null);
        link(ul[2], join); link(uc[2], join); link(ur[2], join);
        int castle = add(4, 0.5, 0.035, "Castle");
        board.get(castle).castle = true; link(join, castle);
        board.get(lc[3]).sticky = true;
    }

    // Wishing Tree — trunk → 3 boughs → 6 sub-boughs → crown castle.
    private void buildTree() {
        theme = themes[4];
        int[] trunk = chain(2, new double[][]{{0.5,0.95},{0.5,0.87},{0.5,0.79},{0.5,0.70}});
        int fork = add(2, 0.5, 0.63, null); link(trunk[3], fork);
        int lbr = add(0, 0.435, 0.575, null);
        int cbr = add(2, 0.50, 0.575, null);
        int rbr = add(4, 0.565, 0.575, null);
        link(fork, lbr, cbr, rbr);                        // 3-way fork
        int[] lb = chain(1, new double[][]{{0.36,0.525},{0.265,0.475},{0.185,0.42}}); link(lbr, lb[0]);
        int[] cb = chain(3, new double[][]{{0.50,0.52},{0.50,0.45},{0.50,0.385}});   link(cbr, cb[0]);
        int[] rb = chain(5, new double[][]{{0.64,0.525},{0.735,0.475},{0.815,0.42}}); link(rbr, rb[0]);
        board.get(cb[2]).specialName = theme.place1;      // Rainbow Glade
        board.get(lb[1]).specialName = theme.place2;      // Sparkle Bridge (shortcut origin)
        board.get(rb[0]).sticky = true;
        // each bough forks into two sub-boughs
        int[] ll = chain(0, new double[][]{{0.12,0.355},{0.075,0.275}});
        int[] lr = chain(2, new double[][]{{0.275,0.35},{0.275,0.265}});
        int[] cl = chain(1, new double[][]{{0.40,0.325},{0.355,0.245}});
        int[] cr = chain(3, new double[][]{{0.60,0.325},{0.645,0.245}});
        int[] rl = chain(4, new double[][]{{0.725,0.35},{0.725,0.265}});
        int[] rr = chain(0, new double[][]{{0.88,0.355},{0.925,0.275}});
        link(lb[2], ll[0], lr[0]);
        link(cb[2], cl[0], cr[0]);
        link(rb[2], rl[0], rr[0]);
        int crown = add(2, 0.5, 0.105, theme.place3);      // Cloud Cake (canopy gateway)
        for (int[] br : new int[][]{ll, lr, cl, cr, rl, rr}) link(br[br.length - 1], crown);
        int castle = add(2, 0.5, 0.045, "Castle");
        board.get(castle).castle = true; link(crown, castle);
        // sparkle bridge shortcut: left bough ↔ right bough
        board.get(lb[1]).shortcut = true; link(lb[1], rb[1]);
    }

    // ─────────────────────────────────────────────────────────────
    //  Turn logic
    // ─────────────────────────────────────────────────────────────

    void drawCard() {
        if (hasBranches) drawCardGraph();
        else drawCardClassic();
    }

    // Original linear behaviour — preserved verbatim.
    private void drawCardClassic() {
        if (deck.isEmpty()) buildDeck();
        Card card = deck.remove(0);
        int old = player;
        if (card.special != null) {
            player = indexOfSpecial(card.special);
            lastCard = "Magic card: go to " + card.special + "!";
        } else {
            player = findNextColor(player + 1, card.colorIndex, card.count);
            lastCard = "Card: " + (card.count == 2 ? "Double " : "") + colorNames[card.colorIndex] + "!";
        }

        Space landed = board.get(player);
        if (landed.shortcut && player < board.size() - 8) {
            player += 4 + random.nextInt(4);
            lastCard += " Rainbow slide forward!";
        } else if (landed.sticky && player > 3) {
            player -= 2;
            lastCard += " Sticky syrup slip back!";
        }
        moves++;
        if (player >= board.size() - 1) {
            player = board.size() - 1;
            lastCard = "You reached the " + theme.name + " Castle in " + moves + " cards! 🎉";
        }
        if (old == player) lastCard += " Stay cozy right here.";
    }

    // Branch-aware movement over the graph.
    private void drawCardGraph() {
        if (branchPending) return;                 // must resolve the fork first
        if (deck.isEmpty()) buildDeck();
        Card card = deck.remove(0);
        moveStartPos = player;
        moves++;
        if (card.special != null) {
            player = indexOfSpecial(card.special);
            recordTrail(player);
            lastCard = "Magic card: go to " + card.special + "!";
            settleGraph();
        } else {
            pendColor = card.colorIndex; pendCount = card.count;
            lastCard = "Card: " + (card.count == 2 ? "Double " : "") + colorNames[card.colorIndex] + "!";
            advanceGraph(player, 0, -1);
            if (branchPending) lastCard += " Choose your path!";
            else settleGraph();
        }
    }

    /** Resolve a pending fork by picking one of the outgoing edges. */
    void chooseBranch(int chosenNextId) {
        if (!branchPending) return;
        if (!branchOptions.contains(chosenNextId)) return;
        branchPending = false;
        int from = branchNode;
        advanceGraph(from, pendHits, chosenNextId);
        if (branchPending) lastCard += " Choose your path!";
        else settleGraph();
    }

    /**
     * Walk the graph from {@code fromNode}, counting colour matches. If a fork is
     * reached before the target count, pause ({@code branchPending}) for the player
     * to choose. {@code firstEdge} forces the first step when resuming after a choice.
     */
    private void advanceGraph(int fromNode, int startHits, int firstEdge) {
        int cur = fromNode;
        int hits = startHits;
        boolean first = true;
        while (true) {
            List<Integer> edges = board.get(cur).nextIds;
            if (edges.isEmpty()) { player = cur; return; }   // reached the castle / dead end
            int next;
            if (edges.size() > 1) {
                if (first && firstEdge >= 0) {
                    next = firstEdge;
                } else {
                    branchPending = true;
                    branchNode = cur;
                    branchOptions.clear();
                    branchOptions.addAll(edges);
                    pendHits = hits;
                    player = cur;
                    return;
                }
            } else {
                next = edges.get(0);
            }
            first = false;
            cur = next;
            recordTrail(cur);
            Space s = board.get(cur);
            if (s.colorIndex == pendColor || s.castle) hits++;
            if (s.castle || hits >= pendCount) { player = cur; return; }
        }
    }

    private void settleGraph() {
        Space s = board.get(player);
        if (s.sticky && trail.size() >= 3) {
            player = trail.get(trail.size() - 3);
            recordTrail(player);
            lastCard += " Sticky syrup slip back!";
        }
        if (player >= board.size() - 1) {
            player = board.size() - 1;
            lastCard = "You reached the " + theme.name + " Castle in " + moves + " cards! 🎉";
        } else if (moveStartPos == player) {
            lastCard += " Stay cozy right here.";
        }
    }

    private void recordTrail(int node) {
        if (trail.isEmpty() || trail.get(trail.size() - 1) != node) trail.add(node);
    }

    int findNextColor(int start, int colorIndex, int count) {
        int hits = 0;
        for (int i = start; i < board.size(); i++) {
            if (board.get(i).colorIndex == colorIndex || board.get(i).castle) {
                hits++;
                if (hits == count) return i;
            }
        }
        return board.size() - 1;
    }

    int indexOfSpecial(String name) {
        for (int i = 0; i < board.size(); i++) {
            if (name.equals(board.get(i).specialName)) return i;
        }
        return player;
    }

    boolean isWon() { return player >= board.size() - 1; }

    List<Space> getBoard() { return board; }
    int getPlayerPosition() { return player; }
    int getMoveCount() { return moves; }
    String getLastCard() { return lastCard; }
    Theme getCurrentTheme() { return theme; }
    String getLayoutId() { return layoutId; }
    boolean isBranchPending() { return branchPending; }
    int getBranchNode() { return branchNode; }
    List<Integer> getBranchOptions() { return branchOptions; }

    static class Space {
        int colorIndex;
        String specialName;
        boolean castle, shortcut, sticky, branch;
        float px, py;                       // normalized board position (py=0 top, py=1 bottom)
        final ArrayList<Integer> nextIds = new ArrayList<>();
        Space(int c, String n, boolean castle) { this.colorIndex = c; this.specialName = n; this.castle = castle; }
    }

    static class Card {
        int colorIndex, count;
        String special;
        Card(int c, int count, String special) { this.colorIndex = c; this.count = count; this.special = special; }
    }

    static class Theme {
        String name, emoji, place1, place2, place3;
        Theme(String n, String e, String a, String b, String c) {
            name = n; emoji = e; place1 = a; place2 = b; place3 = c;
        }
    }
}
