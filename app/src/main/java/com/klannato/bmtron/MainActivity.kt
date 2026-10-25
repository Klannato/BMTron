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
    @Volatile private var running = false

    private var screenWidth = 0
    private var screenHeight = 0

    private var controlZoneHeight = 0
    private var playFieldTop = 0f
    private var playFieldBottom = 0f

    private var gameState = STATE_PLAYING
    private var score1 = 0
    private var score2 = 0

    private var p1x = 0f
    private var p1y = 0f
    private var p1dx = 0f
    private var p1dy = 1f
    private val p1Trail = mutableListOf<Pair<Float, Float>>()
    private var p1Alive = true

    private var p2x = 0f
    private var p2y = 0f
    private var p2dx = 0f
    private var p2dy = -1f
    private val p2Trail = mutableListOf<Pair<Float, Float>>()
    private var p2Alive = true

    private val trailWidth = 4f
    private val playerRadius = 6f
    private val maxTrailSize = 2000

    private val p1Paint = Paint().apply { color = Color.CYAN; style = Paint.Style.FILL; isAntiAlias = true }
    private val p2Paint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.FILL; isAntiAlias = true }
    private val bgPaint = Paint().apply { color = Color.BLACK }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 40f; isAntiAlias = true }
    private val dividerPaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 2f }
    private val controlBgPaint = Paint().apply { color = Color.rgb(30, 30, 30) }
    private val controlBorderPaint = Paint().apply { color = Color.rgb(80, 80, 80); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val controlTextPaint = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val activeControlPaint = Paint().apply { color = Color.rgb(60, 60, 60) }
    private val labelPaint = Paint().apply { color = Color.CYAN; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val label2Paint = Paint().apply { color = Color.MAGENTA; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

    // 4 direction buttons per player: Up, Down, Left, Right
    private var p1UpBtn = RectF()
    private var p1DownBtn = RectF()
    private var p1LeftBtn = RectF()
    private var p1RightBtn = RectF()
    private var p2UpBtn = RectF()
    private var p2DownBtn = RectF()
    private var p2LeftBtn = RectF()
    private var p2RightBtn = RectF()

    private var p1UpPressed = false
    private var p1DownPressed = false
    private var p1LeftPressed = false
    private var p1RightPressed = false
    private var p2UpPressed = false
    private var p2DownPressed = false
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
        controlZoneHeight = (screenHeight * 0.16f).toInt()
        playFieldTop = controlZoneHeight.toFloat()
        playFieldBottom = screenHeight - controlZoneHeight.toFloat()

        val btnSize = controlZoneHeight * 0.45f
        val gap = controlZoneHeight * 0.08f
        val row1Y = controlZoneHeight * 0.08f
        val row2Y = row1Y + btnSize + gap

        // P1 controls: Up/Down on left, Left/Right on right
        val leftX = screenWidth * 0.08f
        val rightX = screenWidth * 0.92f - btnSize
        p1UpBtn = RectF(leftX, row1Y, leftX + btnSize, row1Y + btnSize)
        p1DownBtn = RectF(leftX, row2Y, leftX + btnSize, row2Y + btnSize)
        p1LeftBtn = RectF(rightX, row1Y, rightX + btnSize, row1Y + btnSize)
        p1RightBtn = RectF(rightX, row2Y, rightX + btnSize, row2Y + btnSize)

        val baseY = screenHeight - controlZoneHeight + controlZoneHeight * 0.08f
        p2UpBtn = RectF(leftX, baseY, leftX + btnSize, baseY + btnSize)
        p2DownBtn = RectF(leftX, baseY + btnSize + gap, leftX + btnSize, baseY + btnSize * 2 + gap)
        p2LeftBtn = RectF(rightX, baseY, rightX + btnSize, baseY + btnSize)
        p2RightBtn = RectF(rightX, baseY + btnSize + gap, rightX + btnSize, baseY + btnSize * 2 + gap)

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
        try {
            thread?.join(1000)
        } catch (e: InterruptedException) {
            // ignore
        }
    }

    private fun resetGame() {
        p1x = screenWidth / 2f
        p1y = playFieldTop + (playFieldBottom - playFieldTop) * 0.15f
        p1dx = 0f
        p1dy = 1f
        p1Trail.clear()
        p1Alive = true

        p2x = screenWidth / 2f
        p2y = playFieldBottom - (playFieldBottom - playFieldTop) * 0.15f
        p2dx = 0f
        p2dy = -1f
        p2Trail.clear()
        p2Alive = true

        p1UpPressed = false
        p1DownPressed = false
        p1LeftPressed = false
        p1RightPressed = false
        p2UpPressed = false
        p2DownPressed = false
        p2LeftPressed = false
        p2RightPressed = false

        gameState = STATE_PLAYING
    }

    override fun run() {
        while (running) {
            try {
                if (gameState == STATE_PLAYING) {
                    update()
                }
                draw()
                Thread.sleep(16)
            } catch (e: InterruptedException) {
                // ignore
            } catch (e: Exception) {
                // prevent crash from killing thread
            }
        }
    }

    private fun update() {
        try {
            if (p1Alive) {
                // Determine desired direction from pressed buttons
                var desiredDx = p1dx
                var desiredDy = p1dy
                if (p1UpPressed) { desiredDx = 0f; desiredDy = -1f }
                if (p1DownPressed) { desiredDx = 0f; desiredDy = 1f }
                if (p1LeftPressed) { desiredDx = -1f; desiredDy = 0f }
                if (p1RightPressed) { desiredDx = 1f; desiredDy = 0f }

                // Apply if not a 180-degree reversal
                if (desiredDx != p1dx || desiredDy != p1dy) {
                    if (desiredDx == -p1dx && desiredDy == -p1dy) {
                        // block reversal
                    } else {
                        p1dx = desiredDx
                        p1dy = desiredDy
                    }
                }

                p1Trail.add(Pair(p1x, p1y))
                if (p1Trail.size > maxTrailSize) p1Trail.removeAt(0)
                p1x += p1dx * SPEED
                p1y += p1dy * SPEED

                if (p1x < 0 || p1x > screenWidth || p1y < playFieldTop || p1y > playFieldBottom) {
                    p1Alive = false
                }

                if (p1Alive) {
                    val sz = p1Trail.size
                    for (i in 0 until sz - 5) {
                        val (tx, ty) = p1Trail[i]
                        if (abs(p1x - tx) < trailWidth && abs(p1y - ty) < trailWidth) {
                            p1Alive = false
                            break
                        }
                    }
                }

                if (p1Alive) {
                    for ((tx, ty) in p2Trail) {
                        if (abs(p1x - tx) < trailWidth && abs(p1y - ty) < trailWidth) {
                            p1Alive = false
                            break
                        }
                    }
                }
            }

            if (p2Alive) {
                var desiredDx = p2dx
                var desiredDy = p2dy
                if (p2UpPressed) { desiredDx = 0f; desiredDy = -1f }
                if (p2DownPressed) { desiredDx = 0f; desiredDy = 1f }
                if (p2LeftPressed) { desiredDx = -1f; desiredDy = 0f }
                if (p2RightPressed) { desiredDx = 1f; desiredDy = 0f }

                if (desiredDx != p2dx || desiredDy != p2dy) {
                    if (desiredDx == -p2dx && desiredDy == -p2dy) {
                        // block reversal
                    } else {
                        p2dx = desiredDx
                        p2dy = desiredDy
                    }
                }

                p2Trail.add(Pair(p2x, p2y))
                if (p2Trail.size > maxTrailSize) p2Trail.removeAt(0)
                p2x += p2dx * SPEED
                p2y += p2dy * SPEED

                if (p2x < 0 || p2x > screenWidth || p2y < playFieldTop || p2y > playFieldBottom) {
                    p2Alive = false
                }

                if (p2Alive) {
                    val sz = p2Trail.size
                    for (i in 0 until sz - 5) {
                        val (tx, ty) = p2Trail[i]
                        if (abs(p2x - tx) < trailWidth && abs(p2y - ty) < trailWidth) {
                            p2Alive = false
                            break
                        }
                    }
                }

                if (p2Alive) {
                    for ((tx, ty) in p1Trail) {
                        if (abs(p2x - tx) < trailWidth && abs(p2y - ty) < trailWidth) {
                            p2Alive = false
                            break
                        }
                    }
                }
            }

            if (!p1Alive || !p2Alive) {
                if (!p1Alive && p2Alive) score2++
                else if (p1Alive && !p2Alive) score1++
                gameState = STATE_GAME_OVER
            }
        } catch (e: Exception) {
            // Prevent crash from killing thread
        }
    }

    private fun draw() {
        try {
            val canvas = holder.lockCanvas() ?: return
            canvas.drawPaint(bgPaint)

            canvas.drawRect(0f, 0f, screenWidth.toFloat(), playFieldTop, controlBgPaint)
            canvas.drawRect(0f, playFieldBottom, screenWidth.toFloat(), screenHeight.toFloat(), controlBgPaint)

            canvas.drawLine(0f, playFieldTop, screenWidth.toFloat(), playFieldTop, dividerPaint)
            canvas.drawLine(0f, playFieldBottom, screenWidth.toFloat(), playFieldBottom, dividerPaint)

            // P1 control buttons: Up/Down left, Left/Right right
            drawButton(canvas, p1UpBtn, "▲", p1UpPressed)
            drawButton(canvas, p1DownBtn, "▼", p1DownPressed)
            drawButton(canvas, p1LeftBtn, "◄", p1LeftPressed)
            drawButton(canvas, p1RightBtn, "►", p1RightPressed)

            // P2 control buttons
            drawButton(canvas, p2UpBtn, "▲", p2UpPressed)
            drawButton(canvas, p2DownBtn, "▼", p2DownPressed)
            drawButton(canvas, p2LeftBtn, "◄", p2LeftPressed)
            drawButton(canvas, p2RightBtn, "►", p2RightPressed)

            canvas.drawText("P1", screenWidth / 2f, controlZoneHeight * 0.5f + 8f, labelPaint)
            canvas.drawText("P2", screenWidth / 2f, screenHeight - controlZoneHeight * 0.5f + 8f, label2Paint)

            // Draw trails
            val p1TrailSize = p1Trail.size
            for (i in 1 until p1TrailSize) {
                val (x1, y1) = p1Trail[i - 1]
                val (x2, y2) = p1Trail[i]
                canvas.drawLine(x1, y1, x2, y2, p1Paint.apply { strokeWidth = trailWidth })
            }

            val p2TrailSize = p2Trail.size
            for (i in 1 until p2TrailSize) {
                val (x1, y1) = p2Trail[i - 1]
                val (x2, y2) = p2Trail[i]
                canvas.drawLine(x1, y1, x2, y2, p2Paint.apply { strokeWidth = trailWidth })
            }

            if (p1Alive) canvas.drawCircle(p1x, p1y, playerRadius, p1Paint)
            if (p2Alive) canvas.drawCircle(p2x, p2y, playerRadius, p2Paint)

            canvas.drawText("P1: $score1", 20f, playFieldTop + 40f, textPaint)
            canvas.drawText("P2: $score2", 20f, playFieldBottom - 20f, textPaint)

            if (gameState == STATE_GAME_OVER) {
                val gameOverPaint = Paint().apply { color = Color.WHITE; textSize = 64f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
                canvas.drawText("GAME OVER", screenWidth / 2f, (playFieldTop + playFieldBottom) / 2f, gameOverPaint)
                val restartPaint = Paint().apply { color = Color.GRAY; textSize = 32f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
                canvas.drawText("Tap anywhere to restart", screenWidth / 2f, (playFieldTop + playFieldBottom) / 2f + 60f, restartPaint)
            }

            holder.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            // Prevent crash
        }
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, pressed: Boolean) {
        val paint = if (pressed) activeControlPaint else controlBgPaint
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        canvas.drawRoundRect(rect, 12f, 12f, controlBorderPaint)
        canvas.drawText(label, rect.centerX(), rect.centerY() + 10f, controlTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (gameState == STATE_GAME_OVER) {
                        resetGame()
                        return true
                    }
                    val x = event.x
                    val y = event.y

                    p1UpPressed = p1UpBtn.contains(x, y)
                    p1DownPressed = p1DownBtn.contains(x, y)
                    p1LeftPressed = p1LeftBtn.contains(x, y)
                    p1RightPressed = p1RightBtn.contains(x, y)

                    p2UpPressed = p2UpBtn.contains(x, y)
                    p2DownPressed = p2DownBtn.contains(x, y)
                    p2LeftPressed = p2LeftBtn.contains(x, y)
                    p2RightPressed = p2RightBtn.contains(x, y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    p1UpPressed = false
                    p1DownPressed = false
                    p1LeftPressed = false
                    p1RightPressed = false
                    p2UpPressed = false
                    p2DownPressed = false
                    p2LeftPressed = false
                    p2RightPressed = false
                }
            }
        } catch (e: Exception) {
            // Prevent crash
        }
        return true
    }

    fun pause() {
        running = false
        try {
            thread?.join(1000)
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