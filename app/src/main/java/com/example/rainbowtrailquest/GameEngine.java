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

    Theme theme;
    ArrayList<Space> board = new ArrayList<>();
    ArrayList<Card> deck = new ArrayList<>();
    int player = 0;
    int moves = 0;
    private String lastCard = "Tap Draw Card to start!";

    GameEngine() { this.random = new Random(); }
    GameEngine(Random rng) { this.random = rng; }

    void newGame() {
        theme = themes[random.nextInt(themes.length)];
        buildBoard();
        buildDeck();
        Collections.shuffle(deck, random);
        player = 0;
        moves = 0;
        lastCard = "Welcome to " + theme.name + " " + theme.emoji + " Draw a card!";
    }

    void buildBoard() {
        board.clear();
        int total = 45 + random.nextInt(16);
        for (int i = 0; i < total; i++) {
            board.add(new Space(i % 6, null, false));
        }
        board.get(0).specialName = "Start";
        board.get(board.size() - 1).specialName = "Castle";
        board.get(board.size() - 1).castle = true;

        int[] spots = pickSpecialIndexes(total);
        board.get(spots[0]).specialName = theme.place1;
        board.get(spots[1]).specialName = theme.place2;
        board.get(spots[2]).specialName = theme.place3;

        for (int i = 1; i < board.size() - 1; i++) {
            if (random.nextDouble() < 0.08) board.get(i).shortcut = true;
            if (random.nextDouble() < 0.06) board.get(i).sticky = true;
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

    void drawCard() {
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

    static class Space {
        int colorIndex;
        String specialName;
        boolean castle, shortcut, sticky;
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
