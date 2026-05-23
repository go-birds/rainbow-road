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
    private Button drawButton, newButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 24, 18, 18);

        status = new TextView(this);
        status.setTextSize(18);
        status.setTextColor(Color.rgb(55, 43, 89));
        status.setGravity(Gravity.CENTER);
        status.setPadding(8, 8, 8, 14);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        gameView = new GameView(this, this::setStatus);
        root.addView(gameView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, 16, 0, 0);

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

    interface StatusSink { void set(String s); }

    static class GameView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final StatusSink statusSink;
        private final int[] baseColors = {
            Color.rgb(255, 93, 134), Color.rgb(255, 187, 66), Color.rgb(255, 238, 91),
            Color.rgb(83, 210, 132), Color.rgb(73, 174, 255), Color.rgb(177, 125, 255)
        };
        private GameEngine engine = new GameEngine();
        private int eggSparkles = 0;
        private RectF drawCardRect = new RectF();

        GameView(Context context, StatusSink sink) {
            super(context);
            this.statusSink = sink;
            setBackgroundColor(Color.rgb(255, 248, 253));
        }

        void newGame() {
            engine.newGame();
            statusSink.set(engine.getLastCard());
            invalidate();
        }

        void drawCard() {
            if (engine.isWon()) engine.newGame(); else engine.drawCard();
            statusSink.set(engine.getLastCard());
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            drawBackground(canvas, w, h);
            drawBoard(canvas, w, h);
            drawCardPreview(canvas, w, h);
            drawPlayer(canvas, w, h);
            drawEggs(canvas, w, h);
        }

        private void drawBackground(Canvas c, float w, float h) {
            GameEngine.Theme theme = engine.getCurrentTheme();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 248, 253)); c.drawRect(0, 0, w, h, paint);
            paint.setTextSize(36); paint.setTextAlign(Paint.Align.CENTER); paint.setColor(Color.rgb(97, 74, 138));
            c.drawText(theme.name + " " + theme.emoji, w / 2, 48, paint);
            paint.setTextSize(22); paint.setColor(Color.rgb(117, 95, 154));
            c.drawText("Every game gets a brand-new rainbow trail", w / 2, 78, paint);
        }

        private void drawBoard(Canvas c, float w, float h) {
            List<GameEngine.Space> board = engine.getBoard();
            int cols = 6;
            float top = 110, gap = 8;
            float cell = Math.min((w - 30 - gap * (cols - 1)) / cols, (h - 235) / 10f);
            for (int i = 0; i < board.size(); i++) {
                PointF p = cellCenter(i, cols, cell, gap, top, w);
                GameEngine.Space s = board.get(i);
                paint.setColor(s.castle ? Color.rgb(255, 219, 96) : baseColors[s.colorIndex]);
                paint.setStyle(Paint.Style.FILL);
                c.drawRoundRect(p.x - cell / 2, p.y - cell / 2, p.x + cell / 2, p.y + cell / 2, 18, 18, paint);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.WHITE);
                c.drawRoundRect(p.x - cell / 2, p.y - cell / 2, p.x + cell / 2, p.y + cell / 2, 18, 18, paint);
                paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
                if (s.specialName != null) { paint.setTextSize(20); paint.setColor(Color.rgb(72, 51, 111)); c.drawText(s.castle ? "🏰" : "★", p.x, p.y + 7, paint); }
                else if (s.shortcut) { paint.setTextSize(18); c.drawText("🌈", p.x, p.y + 7, paint); }
                else if (s.sticky) { paint.setTextSize(18); c.drawText("🍯", p.x, p.y + 7, paint); }
            }
        }

        private PointF cellCenter(int i, int cols, float cell, float gap, float top, float w) {
            int row = i / cols;
            int col = i % cols;
            if (row % 2 == 1) col = cols - 1 - col;
            float startX = (w - (cols * cell + (cols - 1) * gap)) / 2 + cell / 2;
            return new PointF(startX + col * (cell + gap), top + row * (cell + gap) + cell / 2);
        }

        private void drawPlayer(Canvas c, float w, float h) {
            GameEngine.Theme theme = engine.getCurrentTheme();
            int playerPos = engine.getPlayerPosition();
            float cell = Math.min((w - 30 - 8 * 5) / 6, (h - 235) / 10f);
            PointF p = cellCenter(playerPos, 6, cell, 8, 110, w);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); c.drawCircle(p.x, p.y, cell * 0.34f, paint);
            paint.setColor(Color.rgb(71, 55, 112)); c.drawCircle(p.x - 6, p.y - 4, 3, paint); c.drawCircle(p.x + 6, p.y - 4, 3, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); c.drawArc(p.x - 10, p.y - 4, p.x + 10, p.y + 12, 20, 140, false, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(22); paint.setTextAlign(Paint.Align.CENTER); c.drawText(theme.emoji, p.x, p.y - cell * 0.45f, paint);
        }

        private void drawCardPreview(Canvas c, float w, float h) {
            String lastCard = engine.getLastCard();
            float y = h - 90;
            drawCardRect.set(24, y - 54, w - 24, y + 46);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); c.drawRoundRect(drawCardRect, 26, 26, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); paint.setColor(Color.rgb(255, 157, 202)); c.drawRoundRect(drawCardRect, 26, 26, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(22); paint.setColor(Color.rgb(74, 57, 118));
            c.drawText(lastCard.length() > 36 ? lastCard.substring(0, 36) + "..." : lastCard, w / 2, y + 8, paint);
        }

        private void drawEggs(Canvas c, float w, float h) {
            if (eggSparkles <= 0) return;
            paint.setTextSize(28); paint.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < eggSparkles; i++) c.drawText(i % 2 == 0 ? "✨" : "🧁", 40 + i * 42, 105, paint);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
            float x = e.getX(), y = e.getY();
            if (drawCardRect.contains(x, y)) { drawCard(); return true; }
            if (y < 90) {
                eggSparkles = (eggSparkles + 1) % 8;
                statusSink.set("Easter egg: sparkle parade unlocked! Tap the title again ✨");
                invalidate(); return true;
            }
            List<GameEngine.Space> board = engine.getBoard();
            float cell = Math.min((getWidth() - 30 - 8 * 5) / 6, (getHeight() - 235) / 10f);
            for (int i = 0; i < board.size(); i++) {
                PointF p = cellCenter(i, 6, cell, 8, 110, getWidth());
                if (Math.abs(x - p.x) < cell / 2 && Math.abs(y - p.y) < cell / 2) {
                    GameEngine.Space s = board.get(i);
                    if (s.specialName != null) statusSink.set("You found " + s.specialName + "!");
                    else if (s.shortcut) statusSink.set("Rainbow spaces slide you forward.");
                    else if (s.sticky) statusSink.set("Honey spaces make silly sticky steps.");
                    else if (i == 7) statusSink.set("Secret joke: Why did the jellybean go to school? To become a smartie!");
                    else statusSink.set("Space " + (i + 1) + ": " + engine.colorNames[s.colorIndex] + " path.");
                    return true;
                }
            }
            return true;
        }
    }
}
