package com.example.rainbowtrailquest;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import java.util.*;

public class MainActivity extends Activity {
    private GameView gameView;
    private TextView status;
    private TextView summary;
    private Button drawButton, newButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, 0);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.rgb(91, 59, 140));
        status.setGravity(Gravity.CENTER);
        status.setPadding(16, 10, 16, 4);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        summary = new TextView(this);
        summary.setTextSize(12);
        summary.setTextColor(Color.rgb(130, 95, 190));
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(16, 2, 16, 8);
        root.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        gameView = new GameView(this, this::setStatus, this::setSummary);
        root.addView(gameView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(16, 6, 16, 16);

        drawButton = new Button(this);
        drawButton.setText("Draw Card");
        drawButton.setOnClickListener(v -> gameView.drawCard());
        buttons.addView(drawButton, new LinearLayout.LayoutParams(0, -2, 1));

        newButton = new Button(this);
        newButton.setText("New Board");
        newButton.setOnClickListener(v -> gameView.newGame());
        buttons.addView(newButton, new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        gameView.newGame();
    }

    private void setStatus(String s) { status.setText(s); }
    private void setSummary(String s) { summary.setText(s); }

    interface StatusSink { void set(String s); }
    interface SummarySink { void set(String s); }

    static class GameView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final StatusSink statusSink;
        private final SummarySink summarySink;

        // Refined rainbow palette
        private final int[] baseColors = {
            0xFFFF4B8D,  // Flamingo
            0xFFFF8C42,  // Papaya
            0xFFFFD23F,  // Saffron
            0xFF3BB273,  // Emerald
            0xFF3D85C8,  // Cobalt
            0xFF9B5DE5,  // Amethyst
        };

        private GameEngine engine = new GameEngine();
        private int eggSparkles = 0;
        private int layoutIndex = 0;
        private final RectF drawCardRect = new RectF();
        private final Path roadPath = new Path();
        // fork-choice UI hit targets
        private final List<RectF> branchButtonRects = new ArrayList<>();
        private final List<Integer> branchButtonIds = new ArrayList<>();

        GameView(Context ctx, StatusSink sink, SummarySink summary) {
            super(ctx);
            statusSink = sink;
            summarySink = summary;
        }

        void newGame() {
            // cycle through the branching boards, then the classic board
            engine.newGame(GameEngine.LAYOUTS[layoutIndex % GameEngine.LAYOUTS.length]);
            layoutIndex++;
            statusSink.set(engine.getLastCard());
            updateSummary();
            invalidate();
        }

        void drawCard() {
            if (engine.isBranchPending()) return;            // resolve the fork on the board first
            if (engine.isWon()) newGame(); else engine.drawCard();
            statusSink.set(engine.getLastCard());
            updateSummary();
            invalidate();
        }

        void chooseBranch(int nextId) {
            engine.chooseBranch(nextId);
            statusSink.set(engine.getLastCard());
            updateSummary();
            invalidate();
        }

        private void updateSummary() {
            int pos = engine.getPlayerPosition();
            int total = engine.getBoard().size();
            int remaining = total - 1 - pos;
            int moves = engine.getMoveCount();
            String s;
            if (engine.isWon()) {
                s = "🏰 Castle reached! · " + moves + " draws";
            } else if (pos == 0) {
                s = "📍 Start · " + remaining + " spaces to castle · " + moves + " draws";
            } else {
                s = "📍 Space " + (pos + 1) + " of " + total + " · " + remaining + " to castle · " + moves + " draws";
            }
            summarySink.set(s);
        }

        // Maps a space to canvas position using its normalized board coordinates.
        // py = 0 is the top (castle), py = 1 is the bottom (start).
        private PointF spaceCenter(int i, float w, float h) {
            GameEngine.Space s = engine.getBoard().get(i);
            float boardTop = 112f, boardBottom = h - 150f;
            float x = s.px * w;
            float y = boardTop + s.py * (boardBottom - boardTop);
            return new PointF(x, y);
        }

        private float spaceRadius(GameEngine.Space s, float cell) {
            if (s.castle) return cell * 1.5f;
            if (s.specialName != null) return cell * 1.2f;
            return cell;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (engine.getCurrentTheme() == null) return;
            float w = getWidth(), h = getHeight();
            List<GameEngine.Space> board = engine.getBoard();
            int n = board.size();
            if (n == 0) return;
            float cell = Math.min(w, h) * 0.033f;

            drawBackground(canvas, w, h);
            drawRoad(canvas, board, w, h, cell);
            if (engine.isBranchPending()) drawBranchEdges(canvas, board, w, h, cell);
            drawSpaces(canvas, board, n, w, h, cell);
            drawPlayerToken(canvas, n, w, h, cell);
            drawCardPreview(canvas, w, h);
            drawEggs(canvas, w);
        }

        private void drawBackground(Canvas c, float w, float h) {
            // Soft lavender-to-blush gradient
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFFF2EAFF, 0xFFFDF5FF, 0xFFEAF1FF},
                null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            GameEngine.Theme theme = engine.getCurrentTheme();
            paint.setFakeBoldText(true);
            paint.setTextSize(h * 0.038f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0xFF5B3B8C);
            c.drawText(theme.name + "  " + theme.emoji, w / 2f, 60f, paint);
            paint.setFakeBoldText(false);
            paint.setTextSize(h * 0.020f);
            paint.setColor(0xFFAA88D8);
            c.drawText("Every path is a new adventure  ✨", w / 2f, 90f, paint);
        }

        private void drawRoad(Canvas c, List<GameEngine.Space> board, float w, float h, float cell) {
            // One path over every graph edge (forks naturally diverge).
            roadPath.reset();
            for (int i = 0; i < board.size(); i++) {
                PointF a = spaceCenter(i, w, h);
                for (int j : board.get(i).nextIds) {
                    PointF b = spaceCenter(j, w, h);
                    boolean dashed = board.get(i).shortcut && Math.abs(j - i) > 1;
                    if (dashed) continue;   // shortcut chords drawn separately
                    roadPath.moveTo(a.x, a.y);
                    roadPath.lineTo(b.x, b.y);
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);

            paint.setStrokeWidth(cell * 2.8f);   // shadow
            paint.setColor(0x22000000);
            c.save(); c.translate(cell * 0.12f, cell * 0.2f);
            c.drawPath(roadPath, paint); c.restore();

            paint.setStrokeWidth(cell * 2.5f);   // white border
            paint.setColor(0xFFFFFFFF);
            c.drawPath(roadPath, paint);

            paint.setStrokeWidth(cell * 1.7f);   // cream surface
            paint.setColor(0xFFFEF6EC);
            c.drawPath(roadPath, paint);

            // dashed shortcut chords
            roadPath.reset();
            boolean any = false;
            for (int i = 0; i < board.size(); i++) {
                if (!board.get(i).shortcut) continue;
                PointF a = spaceCenter(i, w, h);
                for (int j : board.get(i).nextIds) {
                    if (Math.abs(j - i) <= 1) continue;
                    PointF b = spaceCenter(j, w, h);
                    roadPath.moveTo(a.x, a.y); roadPath.lineTo(b.x, b.y); any = true;
                }
            }
            if (any) {
                paint.setStrokeWidth(cell * 0.7f);
                paint.setColor(0xCCB98AD8);
                paint.setPathEffect(new DashPathEffect(new float[]{cell * 0.9f, cell * 0.6f}, 0));
                c.drawPath(roadPath, paint);
                paint.setPathEffect(null);
            }
        }

        // Highlight the diverging options at a pending fork.
        private void drawBranchEdges(Canvas c, List<GameEngine.Space> board, float w, float h, float cell) {
            int from = engine.getBranchNode();
            if (from < 0) return;
            PointF a = spaceCenter(from, w, h);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int j : engine.getBranchOptions()) {
                PointF b = spaceCenter(j, w, h);
                paint.setStrokeWidth(cell * 2.0f);
                paint.setColor(0xAA000000 | (baseColors[board.get(j).colorIndex] & 0xFFFFFF));
                c.drawLine(a.x, a.y, b.x, b.y, paint);
            }
        }

        private void drawSpaces(Canvas c, List<GameEngine.Space> board, int n, float w, float h, float cell) {
            for (int i = 0; i < n; i++) {
                GameEngine.Space s = board.get(i);
                PointF p = spaceCenter(i, w, h);
                int col = s.castle ? 0xFFFFCC00 : baseColors[s.colorIndex];
                float r = spaceRadius(s, cell);

                // Fork halo on branch spaces (gold dashed ring)
                if (s.branch) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(cell * 0.26f);
                    paint.setColor(i == engine.getBranchNode() && engine.isBranchPending() ? 0xFFFFC400 : 0x88F4C95D);
                    paint.setPathEffect(new DashPathEffect(new float[]{cell * 0.5f, cell * 0.34f}, 0));
                    c.drawCircle(p.x, p.y, r + cell * 0.5f, paint);
                    paint.setPathEffect(null);
                }

                // Drop shadow
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x30000000);
                c.drawCircle(p.x + r * 0.12f, p.y + r * 0.16f, r, paint);

                // Outer colored ring
                paint.setColor(col);
                c.drawCircle(p.x, p.y, r, paint);

                // White separator
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(p.x, p.y, r * 0.78f, paint);

                // Inner fill (lightened color)
                paint.setColor(lighten(col, 0.42f));
                c.drawCircle(p.x, p.y, r * 0.66f, paint);

                // Icon
                paint.setTextAlign(Paint.Align.CENTER);
                if (s.castle) {
                    paint.setTextSize(r * 1.08f);
                    c.drawText("🏰", p.x, p.y + r * 0.38f, paint);
                } else if (i == 0) {
                    paint.setTextSize(r * 0.92f);
                    c.drawText("🌟", p.x, p.y + r * 0.33f, paint);
                } else if (s.shortcut) {
                    paint.setTextSize(r * 0.92f);
                    c.drawText("🌈", p.x, p.y + r * 0.33f, paint);
                } else if (s.sticky) {
                    paint.setTextSize(r * 0.92f);
                    c.drawText("🍯", p.x, p.y + r * 0.33f, paint);
                } else if (s.specialName != null) {
                    paint.setTextSize(r * 0.92f);
                    c.drawText("✨", p.x, p.y + r * 0.33f, paint);
                } else if (s.branch) {
                    paint.setTextSize(r * 0.92f);
                    c.drawText("🔀", p.x, p.y + r * 0.33f, paint);
                }
            }
        }

        private void drawPlayerToken(Canvas c, int n, float w, float h, float cell) {
            PointF p = spaceCenter(engine.getPlayerPosition(), w, h);
            float r = cell * 1.18f;

            // Outer glow rings
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x28C090FF);
            c.drawCircle(p.x, p.y, r + cell * 0.60f, paint);
            paint.setColor(0x50C090FF);
            c.drawCircle(p.x, p.y, r + cell * 0.30f, paint);

            // Token body
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(p.x, p.y, r, paint);

            // Amethyst stroke ring
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(cell * 0.30f);
            paint.setColor(0xFF9B5DE5);
            c.drawCircle(p.x, p.y, r - cell * 0.13f, paint);

            // Theme emoji fills the token
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(r * 1.28f);
            c.drawText(engine.getCurrentTheme().emoji, p.x, p.y + r * 0.45f, paint);
        }

        private void drawCardPreview(Canvas c, float w, float h) {
            if (engine.isBranchPending()) { drawBranchChoice(c, w, h); return; }
            String lastCard = engine.getLastCard();
            float cardH = 108f;
            float cardTop = h - cardH - 10f;
            drawCardRect.set(16f, cardTop, w - 16f, cardTop + cardH);

            // Shadow
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x20000000);
            c.drawRoundRect(new RectF(20f, cardTop + 5f, w - 12f, cardTop + cardH + 4f), 24f, 24f, paint);

            // White card
            paint.setColor(0xFFFFFFFF);
            c.drawRoundRect(drawCardRect, 24f, 24f, paint);

            // Rainbow border
            paint.setShader(new LinearGradient(16f, cardTop, w - 16f, cardTop,
                new int[]{0xFFFF4B8D, 0xFFFF8C42, 0xFFFFD23F, 0xFF3BB273, 0xFF3D85C8, 0xFF9B5DE5},
                null, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            c.drawRoundRect(drawCardRect, 24f, 24f, paint);
            paint.setShader(null);

            // Card text
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(h * 0.027f, 36f));
            paint.setColor(0xFF5B3B8C);
            String display = lastCard.length() > 44 ? lastCard.substring(0, 44) + "…" : lastCard;
            c.drawText(display, w / 2f, cardTop + cardH * 0.62f, paint);
        }

        private void drawBranchChoice(Canvas c, float w, float h) {
            branchButtonRects.clear();
            branchButtonIds.clear();
            List<Integer> opts = engine.getBranchOptions();
            int from = engine.getBranchNode();
            float gap = 10f;
            float btnH = 66f;
            float promptH = 30f;
            float panelH = promptH + btnH + 20f;
            float top = h - panelH - 10f;

            // prompt
            paint.setStyle(Paint.Style.FILL);
            paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(24f);
            paint.setColor(0xFFB8860B);
            c.drawText("🔀 Choose your path!", w / 2f, top + promptH * 0.8f, paint);
            paint.setFakeBoldText(false);

            float btnTop = top + promptH + 6f;
            int k = opts.size();
            float btnW = (w - 32f - gap * (k - 1)) / k;
            List<GameEngine.Space> board = engine.getBoard();
            for (int idx = 0; idx < k; idx++) {
                int nextId = opts.get(idx);
                GameEngine.Space ns = board.get(nextId);
                float left = 16f + idx * (btnW + gap);
                RectF r = new RectF(left, btnTop, left + btnW, btnTop + btnH);
                branchButtonRects.add(r);
                branchButtonIds.add(nextId);

                int col = ns.castle ? 0xFFFFCC00 : baseColors[ns.colorIndex];
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(col);
                c.drawRoundRect(r, 16f, 16f, paint);
                paint.setColor(lighten(col, 0.5f));
                c.drawRoundRect(new RectF(r.left + 3, r.top + 3, r.right - 3, r.top + btnH * 0.5f), 14f, 14f, paint);

                // label: landmark name, or direction + colour
                String label;
                if (ns.specialName != null) label = ns.specialName;
                else {
                    String dir = k == 2 ? (ns.px < board.get(from).px ? "Left" : "Right")
                                        : (ns.px < 0.4f ? "Left" : ns.px > 0.6f ? "Right" : "Center");
                    label = dir;
                }
                paint.setColor(0xFF3A2A1A);
                paint.setFakeBoldText(true);
                paint.setTextSize(label.length() > 10 ? 18f : 22f);
                String disp = label.length() > 16 ? label.substring(0, 15) + "…" : label;
                c.drawText(disp, r.centerX(), r.centerY() + 8f, paint);
                paint.setFakeBoldText(false);
            }
        }

        private void drawEggs(Canvas c, float w) {
            if (eggSparkles <= 0) return;
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(30f);
            paint.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < eggSparkles; i++)
                c.drawText(i % 2 == 0 ? "✨" : "🧁", 44f + i * 52f, 96f, paint);
        }

        private int lighten(int color, float f) {
            return Color.rgb(
                Math.min(255, (int)(Color.red(color)   + (255 - Color.red(color))   * f)),
                Math.min(255, (int)(Color.green(color) + (255 - Color.green(color)) * f)),
                Math.min(255, (int)(Color.blue(color)  + (255 - Color.blue(color))  * f))
            );
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
            float x = e.getX(), y = e.getY();

            // fork choice takes priority while a branch is pending
            if (engine.isBranchPending()) {
                for (int i = 0; i < branchButtonRects.size(); i++) {
                    if (branchButtonRects.get(i).contains(x, y)) {
                        chooseBranch(branchButtonIds.get(i));
                        return true;
                    }
                }
                return true;
            }

            if (drawCardRect.contains(x, y)) { drawCard(); return true; }
            if (y < 100f) {
                eggSparkles = (eggSparkles + 1) % 8;
                statusSink.set("Easter egg: sparkle parade unlocked! Tap the title again ✨");
                invalidate();
                return true;
            }
            List<GameEngine.Space> board = engine.getBoard();
            int n = board.size();
            float w = getWidth(), h = getHeight();
            float cell = Math.min(w, h) * 0.033f;
            for (int i = 0; i < n; i++) {
                PointF p = spaceCenter(i, w, h);
                float r = spaceRadius(board.get(i), cell);
                if (Math.hypot(x - p.x, y - p.y) <= r * 1.2f) {
                    GameEngine.Space s = board.get(i);
                    if (s.specialName != null) statusSink.set("✨ You found " + s.specialName + "!");
                    else if (s.shortcut) statusSink.set("🌈 Rainbow slide — zoom forward!");
                    else if (s.sticky) statusSink.set("🍯 Honey trap — slip back 2 spaces!");
                    else if (i == 7) statusSink.set("Secret: Why did the jellybean go to school? To become a smartie!");
                    else statusSink.set("Space " + (i + 1) + ": " + engine.colorNames[s.colorIndex] + " path.");
                    return true;
                }
            }
            return true;
        }
    }
}
