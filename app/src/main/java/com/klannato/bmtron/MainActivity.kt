package com.klannato.bmtron

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private val holder: SurfaceHolder = getHolder()
    private var thread: Thread? = null
    private var running = false

    private var screenWidth = 0
    private var screenHeight = 0

    // Layout zones
    private var controlZoneHeight = 0
    private var playFieldTop = 0f
    private var playFieldBottom = 0f

    // Game state
    private var gameState = STATE_PLAYING
    private var score1 = 0
    private var score2 = 0

    // Player 1 (top) - starts at top of play field, moves down
    private var p1x = 0f
    private var p1y = 0f
    private var p1dx = 0f
    private var p1dy = 1f
    private val p1Trail = mutableListOf<Pair<Float, Float>>()
    private var p1Alive = true

    // Player 2 (bottom) - starts at bottom of play field, moves up
    private var p2x = 0f
    private var p2y = 0f
    private var p2dx = 0f
    private var p2dy = -1f
    private val p2Trail = mutableListOf<Pair<Float, Float>>()
    private var p2Alive = true

    private val trailWidth = 4f
    private val playerRadius = 6f

    // Paints
    private val p1Paint = Paint().apply { color = Color.CYAN; style = Paint.Style.FILL; isAntiAlias = true }
    private val p2Paint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.FILL; isAntiAlias = true }
    private val bgPaint = Paint().apply { color = Color.BLACK }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 40f; isAntiAlias = true }
    private val dividerPaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 2f }
    private val controlBgPaint = Paint().apply { color = Color.rgb(30, 30, 30) }
    private val controlBorderPaint = Paint().apply { color = Color.rgb(80, 80, 80); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val controlTextPaint = Paint().apply { color = Color.WHITE; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val activeControlPaint = Paint().apply { color = Color.rgb(60, 60, 60) }
    private val labelPaint = Paint().apply { color = Color.CYAN; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val label2Paint = Paint().apply { color = Color.MAGENTA; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

    // Control button rects
    private var p1LeftBtn = RectF()
    private var p1RightBtn = RectF()
    private var p2LeftBtn = RectF()
    private var p2RightBtn = RectF()

    // Touch state
    private var p1LeftPressed = false
    private var p1RightPressed = false
    private var p2LeftPressed = false
    private var p2RightPressed = false

    companion object {
        const val STATE_PLAYING = 0
        const val STATE_GAME_OVER = 1
        const val SPEED = 3f
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        controlZoneHeight = (screenHeight * 0.12f).toInt()
        playFieldTop = controlZoneHeight.toFloat()
        playFieldBottom = screenHeight - controlZoneHeight.toFloat()

        val btnW = screenWidth / 4f
        val btnH = controlZoneHeight * 0.7f
        val btnY1 = controlZoneHeight * 0.15f
        p1LeftBtn = RectF(screenWidth * 0.05f, btnY1, screenWidth * 0.05f + btnW, btnY1 + btnH)
        p1RightBtn = RectF(screenWidth * 0.95f - btnW, btnY1, screenWidth * 0.95f, btnY1 + btnH)

        val btnY2 = screenHeight - controlZoneHeight + controlZoneHeight * 0.15f
        p2LeftBtn = RectF(screenWidth * 0.05f, btnY2, screenWidth * 0.05f + btnW, btnY2 + btnH)
        p2RightBtn = RectF(screenWidth * 0.95f - btnW, btnY2, screenWidth * 0.95f, btnY2 + btnH)

        resetGame()
        running = true
        thread = Thread(this)
        thread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join()
    }

    private fun resetGame() {
        p1x = screenWidth / 2f
        p1y = playFieldTop + (playFieldBottom - playFieldTop) * 0.1f
        p1dx = 0f
        p1dy = 1f
        p1Trail.clear()
        p1Alive = true

        p2x = screenWidth / 2f
        p2y = playFieldBottom - (playFieldBottom - playFieldTop) * 0.1f
        p2dx = 0f
        p2dy = -1f
        p2Trail.clear()
        p2Alive = true

        p1LeftPressed = false
        p1RightPressed = false
        p2LeftPressed = false
        p2RightPressed = false

        gameState = STATE_PLAYING
    }

    override fun run() {
        while (running) {
            if (gameState == STATE_PLAYING) {
                update()
            }
            draw()
            try {
                Thread.sleep(16)
            } catch (e: InterruptedException) {
                // ignore
            }
        }
    }

    private fun update() {
        if (p1Alive) {
            if (p1LeftPressed && !p1RightPressed) {
                if (p1dy != 0f) { p1dx = -1f; p1dy = 0f }
            } else if (p1RightPressed && !p1LeftPressed) {
                if (p1dy != 0f) { p1dx = 1f; p1dy = 0f }
            } else if (!p1LeftPressed && !p1RightPressed) {
                if (p1dx != 0f) {
                    p1dx = 0f
                    p1dy = 1f
                }
            }

            p1Trail.add(Pair(p1x, p1y))
            p1x += p1dx * SPEED
            p1y += p1dy * SPEED

            if (p1x < 0 || p1x > screenWidth || p1y < playFieldTop || p1y > playFieldBottom) {
                p1Alive = false
            }

            for (i in 0 until p1Trail.size - 10) {
                val (tx, ty) = p1Trail[i]
                if (abs(p1x - tx) < trailWidth && abs(p1y - ty) < trailWidth) {
                    p1Alive = false
                    break
                }
            }

            for ((tx, ty) in p2Trail) {
                if (abs(p1x - tx) < trailWidth && abs(p1y - ty) < trailWidth) {
                    p1Alive = false
                    break
                }
            }
        }

        if (p2Alive) {
            if (p2LeftPressed && !p2RightPressed) {
                if (p2dy != 0f) { p2dx = -1f; p2dy = 0f }
            } else if (p2RightPressed && !p2LeftPressed) {
                if (p2dy != 0f) { p2dx = 1f; p2dy = 0f }
            } else if (!p2LeftPressed && !p2RightPressed) {
                if (p2dx != 0f) {
                    p2dx = 0f
                    p2dy = -1f
                }
            }

            p2Trail.add(Pair(p2x, p2y))
            p2x += p2dx * SPEED
            p2y += p2dy * SPEED

            if (p2x < 0 || p2x > screenWidth || p2y < playFieldTop || p2y > playFieldBottom) {
                p2Alive = false
            }

            for (i in 0 until p2Trail.size - 10) {
                val (tx, ty) = p2Trail[i]
                if (abs(p2x - tx) < trailWidth && abs(p2y - ty) < trailWidth) {
                    p2Alive = false
                    break
                }
            }

            for ((tx, ty) in p1Trail) {
                if (abs(p2x - tx) < trailWidth && abs(p2y - ty) < trailWidth) {
                    p2Alive = false
                    break
                }
            }
        }

        if (!p1Alive || !p2Alive) {
            if (!p1Alive && p2Alive) score2++
            else if (p1Alive && !p2Alive) score1++
            gameState = STATE_GAME_OVER
        }
    }

    private fun draw() {
        val canvas = holder.lockCanvas() ?: return
        canvas.drawPaint(bgPaint)

        // Draw control zones
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), playFieldTop, controlBgPaint)
        canvas.drawRect(0f, playFieldBottom, screenWidth.toFloat(), screenHeight.toFloat(), controlBgPaint)

        // Draw divider lines
        canvas.drawLine(0f, playFieldTop, screenWidth.toFloat(), playFieldTop, dividerPaint)
        canvas.drawLine(0f, playFieldBottom, screenWidth.toFloat(), playFieldBottom, dividerPaint)

        // Draw control buttons
        drawButton(canvas, p1LeftBtn, "◄", p1LeftPressed)
        drawButton(canvas, p1RightBtn, "►", p1RightPressed)
        drawButton(canvas, p2LeftBtn, "◄", p2LeftPressed)
        drawButton(canvas, p2RightBtn, "►", p2RightPressed)

        // Draw labels
        canvas.drawText("P1", screenWidth / 2f, controlZoneHeight * 0.5f + 8f, labelPaint)
        canvas.drawText("P2", screenWidth / 2f, screenHeight - controlZoneHeight * 0.5f + 8f, label2Paint)

        // Draw P1 trail
        for (i in 1 until p1Trail.size) {
            val (x1, y1) = p1Trail[i - 1]
            val (x2, y2) = p1Trail[i]
            canvas.drawLine(x1, y1, x2, y2, p1Paint.apply { strokeWidth = trailWidth })
        }

        // Draw P2 trail
        for (i in 1 until p2Trail.size) {
            val (x1, y1) = p2Trail[i - 1]
            val (x2, y2) = p2Trail[i]
            canvas.drawLine(x1, y1, x2, y2, p2Paint.apply { strokeWidth = trailWidth })
        }

        // Draw players
        if (p1Alive) canvas.drawCircle(p1x, p1y, playerRadius, p1Paint)
        if (p2Alive) canvas.drawCircle(p2x, p2y, playerRadius, p2Paint)

        // Draw scores
        canvas.drawText("P1: $score1", 20f, playFieldTop + 40f, textPaint)
        canvas.drawText("P2: $score2", 20f, playFieldBottom - 20f, textPaint)

        if (gameState == STATE_GAME_OVER) {
            val gameOverPaint = Paint().apply { color = Color.WHITE; textSize = 64f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
            canvas.drawText("GAME OVER", screenWidth / 2f, (playFieldTop + playFieldBottom) / 2f, gameOverPaint)
            val restartPaint = Paint().apply { color = Color.GRAY; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
            canvas.drawText("Tap anywhere to restart", screenWidth / 2f, (playFieldTop + playFieldBottom) / 2f + 60f, restartPaint)
        }

        holder.unlockCanvasAndPost(canvas)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, pressed: Boolean) {
        val paint = if (pressed) activeControlPaint else controlBgPaint
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        canvas.drawRoundRect(rect, 12f, 12f, controlBorderPaint)
        canvas.drawText(label, rect.centerX(), rect.centerY() + 10f, controlTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (gameState == STATE_GAME_OVER) {
                    resetGame()
                    return true
                }
                val x = event.x
                val y = event.y

                p1LeftPressed = p1LeftBtn.contains(x, y)
                p1RightPressed = p1RightBtn.contains(x, y)

                p2LeftPressed = p2LeftBtn.contains(x, y)
                p2RightPressed = p2RightBtn.contains(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                p1LeftPressed = false
                p1RightPressed = false
                p2LeftPressed = false
                p2RightPressed = false
            }
        }
        return true
    }

    fun pause() {
        running = false
        try {
            thread?.join()
        } catch (e: InterruptedException) {
            // ignore
        }
    }

    fun resume() {
        if (!running) {
            running = true
            thread = Thread(this)
            thread?.start()
        }
    }
}