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
        private final RectF drawCardRect = new RectF();
        private final Path roadPath = new Path();
        private final Path arrowPath = new Path();

        GameView(Context ctx, StatusSink sink, SummarySink summary) {
            super(ctx);
            statusSink = sink;
            summarySink = summary;
        }

        void newGame() {
            engine.newGame();
            statusSink.set(engine.getLastCard());
            updateSummary();
            invalidate();
        }

        void drawCard() {
            if (engine.isWon()) engine.newGame(); else engine.drawCard();
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

        // Maps space index to canvas position: start at bottom, castle at top, sinusoidal winding
        private PointF spaceCenter(int i, int n, float w, float h) {
            float boardTop = 108f, boardBottom = h - 140f;
            float t = (float) i / Math.max(n - 1, 1);
            float y = boardBottom - t * (boardBottom - boardTop);
            float x = w / 2f + w * 0.32f * (float) Math.sin(t * Math.PI * 4.5f);
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
            drawRoad(canvas, n, w, h, cell);
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

        private void drawRoad(Canvas c, int n, float w, float h, float cell) {
            // Build a smooth quadratic-bezier path through all space centers
            PointF prev = spaceCenter(0, n, w, h);
            roadPath.reset();
            roadPath.moveTo(prev.x, prev.y);
            for (int i = 1; i < n; i++) {
                PointF curr = spaceCenter(i, n, w, h);
                float mx = (prev.x + curr.x) / 2f, my = (prev.y + curr.y) / 2f;
                roadPath.quadTo(prev.x, prev.y, mx, my);
                prev = curr;
            }
            roadPath.lineTo(prev.x, prev.y);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);

            // Drop shadow
            paint.setStrokeWidth(cell * 2.8f);
            paint.setColor(0x22000000);
            c.save();
            c.translate(cell * 0.15f, cell * 0.22f);
            c.drawPath(roadPath, paint);
            c.restore();

            // White outer road
            paint.setStrokeWidth(cell * 2.5f);
            paint.setColor(0xFFFFFFFF);
            c.drawPath(roadPath, paint);

            // Cream road surface
            paint.setStrokeWidth(cell * 1.7f);
            paint.setColor(0xFFFEF6EC);
            c.drawPath(roadPath, paint);

            // Direction arrows every ~10% of board length
            paint.setStyle(Paint.Style.FILL);
            int step = Math.max(3, n / 10);
            for (int i = step; i < n - 1; i += step) {
                drawArrow(c, spaceCenter(i, n, w, h), spaceCenter(i + 1, n, w, h), cell * 0.58f);
            }
        }

        private void drawArrow(Canvas c, PointF from, PointF to, float sz) {
            float ang = (float) Math.atan2(to.y - from.y, to.x - from.x);
            float mx = (from.x + to.x) / 2f, my = (from.y + to.y) / 2f;
            arrowPath.reset();
            arrowPath.moveTo(mx + sz * (float) Math.cos(ang),
                             my + sz * (float) Math.sin(ang));
            arrowPath.lineTo(mx + sz * 0.52f * (float) Math.cos(ang + 2.5f),
                             my + sz * 0.52f * (float) Math.sin(ang + 2.5f));
            arrowPath.lineTo(mx + sz * 0.52f * (float) Math.cos(ang - 2.5f),
                             my + sz * 0.52f * (float) Math.sin(ang - 2.5f));
            arrowPath.close();
            paint.setColor(0x55B09ECC);
            c.drawPath(arrowPath, paint);
        }

        private void drawSpaces(Canvas c, List<GameEngine.Space> board, int n, float w, float h, float cell) {
            for (int i = 0; i < n; i++) {
                GameEngine.Space s = board.get(i);
                PointF p = spaceCenter(i, n, w, h);
                int col = s.castle ? 0xFFFFCC00 : baseColors[s.colorIndex];
                float r = spaceRadius(s, cell);

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
                }
            }
        }

        private void drawPlayerToken(Canvas c, int n, float w, float h, float cell) {
            PointF p = spaceCenter(engine.getPlayerPosition(), n, w, h);
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
                PointF p = spaceCenter(i, n, w, h);
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
